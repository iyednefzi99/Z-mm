package com.zumm;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Le flux OIDC joue pour de vrai, contre un VRAI Keycloak (SPRINT-34).
 *
 * <p>{@code REVUE-CONSOLIDEE.md} §5 le pointait depuis le SPRINT-11 : « 19
 * tests, mais tous avec un Keycloak simule — precisement l'angle mort qui
 * avait laisse passer l'absence de rafraichissement [de jeton] ». Chaque test
 * OIDC du depot (`BffSessionIT`, `RbacIT`, ...) fabrique un jeton a la main
 * via {@code oidcLogin()}/{@code jwt()} — aucun n'a jamais fait emettre un
 * jeton par un vrai royaume, ni verifie sa signature contre de vraies cles
 * publiques.
 *
 * <p><strong>Le chemin choisi.</strong> Zumm offre deux flux OIDC (ADR-009) :
 * la redirection classique Authorization Code + PKCE ({@code oauth2Login()},
 * client {@code zumm-frontend}) et l'echange direct mene par le BFF
 * ({@code POST /bff/connexion}, client {@code zumm-bff}). Le premier est
 * vestige — la PWA ne l'emprunte plus, seule l'inscription et la connexion
 * dans l'application existent en pratique. Ce test joue donc le SECOND : la
 * ou l'application envoie vraiment un mot de passe, ou {@code IdentiteService}
 * appelle vraiment le point de jeton du royaume, et ou {@code JwtDecoder}
 * valide vraiment signature, emetteur ET audience d'un jeton emis par
 * Keycloak — jamais exerce jusqu'ici, sous aucune forme.
 *
 * <p>Realm importe : {@code infra/keycloak/realm-zumm.dev.json}, celui-la meme
 * que la pile de developpement — memes comptes de test ({@code apiculteur-test}
 * / {@code admin-test}, mot de passe {@code test}), meme secret de client
 * {@code zumm-bff} (defauts de {@code IdentiteService}, aucun a surcharger).
 * Aucune base Postgres cote Keycloak : {@code start-dev} sans {@code KC_DB}
 * suffit a un realm ephemere, et evite de le brancher au conteneur
 * PostgreSQL de l'application.
 *
 * <p><strong>Le serveur de test parle un vrai TLS.</strong> {@code
 * application.yml} pose {@code server.servlet.session.cookie.secure: true}
 * SANS condition — choix argumente en commentaire (un cookie non marque
 * {@code Secure} avait force un {@code redirect_uri} en {@code http://}), et
 * le mecanisme CSRF ({@code CookieCsrfTokenRepository}) hérite lui aussi de
 * {@code request.isSecure()} pour son propre cookie. Un client de test qui
 * parlerait en clair recevrait ces cookies mais ne les RENVERRAIT jamais —
 * {@link CookieManager} respecte l'attribut {@code Secure} a la lettre,
 * contrairement aux navigateurs qui font une exception pour {@code
 * localhost}. Plutot que d'affaiblir la configuration (ce que
 * {@code CLAUDE.md} interdit sans reprendre l'argument), Tomcat sert ici un
 * certificat auto-signe genere a la volee par {@code keytool} (outil du JDK,
 * aucune dependance ajoutee) ; le client HTTP ne fait confiance qu'a CE
 * certificat precis — jamais de {@code TrustManager} qui accepte tout.
 *
 * <p>Le navigateur est simule au plus pres de ce qu'une PWA fait reellement :
 * un {@link HttpClient} avec pot a cookies, qui lit le cookie {@code
 * XSRF-TOKEN} (lisible par script, ADR-006) pour le renvoyer en en-tete
 * {@code X-XSRF-TOKEN} — CSRF reste EXIGE sur la chaine navigateur
 * ({@code SecurityConfig}), meme sur l'endpoint de connexion.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class AuthentificationOidcReelleIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("zumm/test-postgres:16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("zumm")
            .withUsername("zumm")
            .withPassword("zumm_secure")
            .withCommand("postgres", "-c", "shared_preload_libraries=timescaledb");

    /**
     * Un vrai Keycloak, pas un double. Le realm de DEV (comptes de test connus)
     * est copie dans le repertoire d'import — {@code MountableFile}, pas une
     * ressource de classpath : dupliquer {@code infra/keycloak/realm-zumm.dev.json}
     * sous {@code src/test/resources} ferait vivre deux copies vouees a diverger.
     */
    @Container
    static GenericContainer<?> keycloak = new GenericContainer<>(
            DockerImageName.parse("quay.io/keycloak/keycloak:26.0"))
            .withExposedPorts(8080)
            .withEnv("KC_HTTP_ENABLED", "true")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(cheminRealmDev()),
                    "/opt/keycloak/data/import/realm-zumm-dev.json")
            .withCommand("start-dev", "--import-realm")
            .waitingFor(Wait.forHttp("/realms/zumm/.well-known/openid-configuration")
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    private static final String MOT_DE_PASSE_KEYSTORE = "test-only-non-secret";

    private static Path keystoreServeur;
    private static Path certificatServeur;

    /**
     * Chemin du realm de dev, relatif a la racine du depot — le module backend
     * tourne avec cette racine comme repertoire courant (Surefire/Failsafe).
     */
    private static String cheminRealmDev() {
        return Path.of(System.getProperty("user.dir"), "..", "infra", "keycloak", "realm-zumm.dev.json")
                .normalize().toString();
    }

    /**
     * Certificat auto-signe jetable, genere par {@code keytool} (JDK, aucune
     * dependance) dans un dossier temporaire propre a cette execution.
     */
    private static void genererCertificatDeTest() throws Exception {
        Path dossier = Files.createTempDirectory("zumm-oidc-it-tls");
        keystoreServeur = dossier.resolve("keystore.p12");
        certificatServeur = dossier.resolve("certificat.crt");
        String keytool = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "keytool.exe" : "keytool")
                .toString();

        executer(keytool, "-genkeypair", "-alias", "test", "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "1", "-storetype", "PKCS12",
                "-keystore", keystoreServeur.toString(), "-storepass", MOT_DE_PASSE_KEYSTORE,
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1");
        executer(keytool, "-exportcert", "-alias", "test",
                "-keystore", keystoreServeur.toString(), "-storepass", MOT_DE_PASSE_KEYSTORE,
                "-file", certificatServeur.toString(), "-rfc");
    }

    private static void executer(String... commande) throws Exception {
        Process processus = new ProcessBuilder(commande).redirectErrorStream(true).start();
        String sortie = new String(processus.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!processus.waitFor(30, TimeUnit.SECONDS) || processus.exitValue() != 0) {
            throw new IllegalStateException("Echec keytool : " + sortie);
        }
    }

    /**
     * L'emetteur des jetons ET le point ou le BFF appelle Keycloak sont la MEME
     * adresse ici — {@code localhost:<port mappe>} — contrairement a la pile
     * Docker ou navigateur et conteneurs ne voient pas Keycloak par le meme nom
     * (piege documente dans {@code docker-compose.dev.yml} et {@code IdentiteService}).
     * Un seul chemin reseau : rien a distinguer entre {@code ZUMM_OIDC_ISSUER_URI}
     * et {@code ZUMM_OIDC_INTERNE}, le second retombant sur le premier par defaut.
     */
    @DynamicPropertySource
    static void proprietes(DynamicPropertyRegistry registry) throws Exception {
        registry.add("ZUMM_OIDC_ISSUER_URI",
                () -> "http://" + keycloak.getHost() + ":" + keycloak.getMappedPort(8080) + "/realms/zumm");

        genererCertificatDeTest();
        registry.add("server.ssl.enabled", () -> "true");
        registry.add("server.ssl.key-store", () -> "file:" + keystoreServeur);
        registry.add("server.ssl.key-store-password", () -> MOT_DE_PASSE_KEYSTORE);
        registry.add("server.ssl.key-store-type", () -> "PKCS12");
        registry.add("server.ssl.key-alias", () -> "test");
    }

    @LocalServerPort
    private int port;

    private final ObjectMapper json = new ObjectMapper();

    private String base() {
        return "https://localhost:" + port;
    }

    /**
     * Un « navigateur » : pot a cookies propre, comme un onglet neuf, qui ne
     * fait confiance qu'au certificat genere pour cette execution.
     */
    private HttpClient navigateur() throws Exception {
        KeyStore confiance = KeyStore.getInstance(KeyStore.getDefaultType());
        confiance.load(null, null);
        try (var flux = Files.newInputStream(certificatServeur)) {
            confiance.setCertificateEntry("test",
                    CertificateFactory.getInstance("X.509").generateCertificate(flux));
        }
        TrustManagerFactory gestionnaireConfiance =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        gestionnaireConfiance.init(confiance);
        SSLContext tls = SSLContext.getInstance("TLS");
        tls.init(null, gestionnaireConfiance.getTrustManagers(), null);

        return HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .sslContext(tls)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    private HttpRequest.Builder requete(String chemin) {
        return HttpRequest.newBuilder(URI.create(base() + chemin));
    }

    private String cookie(HttpClient nav, String nom) {
        CookieManager gestionnaire = (CookieManager) nav.cookieHandler().orElseThrow();
        return gestionnaire.getCookieStore().getCookies().stream()
                .filter(c -> nom.equals(c.getName()))
                .map(HttpCookie::getValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Cookie " + nom + " absent"));
    }

    /**
     * Amorce le pot a cookies : une decouverte faite en ecrivant ce test, que ni
     * {@code BffSessionIT} ni aucun autre test ne pouvait voir — tous fabriquent
     * leur jeton CSRF via {@code csrf()} plutot que de le recevoir pour de vrai.
     *
     * <p>{@code CsrfFilter} ne resout ({@code DeferredCsrfToken#get()}, seul
     * moment ou {@code CookieCsrfTokenRepository} pose le cookie) que sur une
     * methode qui EXIGE la protection — jamais sur un GET. Un navigateur qui
     * n'a encore aucun cookie ne peut donc pas en obtenir un par une lecture
     * prealable : seule une premiere MUTATION, meme rejetee faute de jeton,
     * declenche la generation ET le depot du cookie sur SA PROPRE reponse. Le
     * premier essai de connexion d'un navigateur neuf echoue donc une fois par
     * construction ; c'est ce que ce test rejoue plutot que de le contourner.
     *
     * <p><strong>401, pas 403.</strong> Le refus CSRF d'une requete ANONYME ne
     * passe pas par {@code GestionnaireRefusAcces} (SPRINT-34) : {@code
     * ExceptionTranslationFilter} route une {@code AccessDeniedException} vers
     * l'{@code AuthenticationEntryPoint} tant que personne n'est authentifie, et
     * non vers le gestionnaire de refus — celui-ci ne voit que les refus d'un
     * appelant DEJA identifie. Sans en-tete {@code Accept: text/html}, ce point
     * d'entree rend un 401 nu plutot que de rediriger vers Keycloak.
     */
    private void amorcerCsrf(HttpClient nav) throws Exception {
        HttpRequest requete = requete("/bff/connexion")
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString("{\"identifiant\":\"amorce\",\"motDePasse\":\"amorce\"}"))
                .build();
        HttpResponse<Void> reponse = nav.send(requete, BodyHandlers.discarding());
        assertThat(reponse.statusCode())
                .as("le premier essai, sans jeton CSRF, doit etre rejete — sinon la decouverte ci-dessus est perimee")
                .isEqualTo(401);
    }

    private HttpResponse<String> connexion(HttpClient nav, String identifiant, String motDePasse)
            throws Exception {
        amorcerCsrf(nav);
        HttpRequest requete = requete("/bff/connexion")
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", cookie(nav, "XSRF-TOKEN"))
                .POST(BodyPublishers.ofString(
                        "{\"identifiant\":\"%s\",\"motDePasse\":\"%s\"}".formatted(identifiant, motDePasse)))
                .build();
        return nav.send(requete, BodyHandlers.ofString());
    }

    private HttpResponse<String> session(HttpClient nav) throws Exception {
        return nav.send(requete("/bff/session").GET().build(), BodyHandlers.ofString());
    }

    private HttpResponse<String> creerFermier(HttpClient nav) throws Exception {
        HttpRequest requete = requete("/api/fermiers")
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", cookie(nav, "XSRF-TOKEN"))
                .POST(BodyPublishers.ofString("{\"nom\":\"Rucher OIDC reel\",\"contact\":null}"))
                .build();
        return nav.send(requete, BodyHandlers.ofString());
    }

    @Test
    @DisplayName("connexion reelle : Keycloak emet un jeton, le decodeur le valide, la session reflete le vrai role")
    void connexionReelleEtSession() throws Exception {
        HttpClient nav = navigateur();

        HttpResponse<String> reponseConnexion = connexion(nav, "apiculteur-test", "test");
        assertThat(reponseConnexion.statusCode()).isEqualTo(204);

        HttpResponse<String> reponseSession = session(nav);
        assertThat(reponseSession.statusCode()).isEqualTo(200);
        JsonNode corps = json.readTree(reponseSession.body());
        assertThat(corps.get("utilisateur").asText()).isEqualTo("apiculteur-test");
        assertThat(corps.get("exploitation").asText()).isEqualTo("exploitation-demo");
        assertThat(corps.get("roles")).extracting(JsonNode::asText).containsExactly("apiculteur");
    }

    @Test
    @DisplayName("mot de passe faux : Keycloak refuse, aucune session ne s'ouvre")
    void motDePasseFauxRefuse() throws Exception {
        HttpClient nav = navigateur();

        HttpResponse<String> reponseConnexion = connexion(nav, "apiculteur-test", "mauvais-mot-de-passe");
        assertThat(reponseConnexion.statusCode()).isEqualTo(401);

        assertThat(session(nav).statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("RBAC sur un vrai role Keycloak : refuse a l'apiculteur, permis a l'admin")
    void rbacSurRoleReel() throws Exception {
        HttpClient navApiculteur = navigateur();
        connexion(navApiculteur, "apiculteur-test", "test");
        assertThat(creerFermier(navApiculteur).statusCode()).isEqualTo(403);

        HttpClient navAdmin = navigateur();
        connexion(navAdmin, "admin-test", "test");
        assertThat(creerFermier(navAdmin).statusCode()).isEqualTo(201);
    }
}
