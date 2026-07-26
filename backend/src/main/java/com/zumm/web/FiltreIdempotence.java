package com.zumm.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Rend les mutations idempotentes quand le client fournit une cle (US-055).
 *
 * <p>Le besoin vient du terrain : la PWA met en file les saisies faites hors
 * ligne et les rejoue au retour du reseau. Or un appel reseau peut echouer APRES
 * avoir ete traite par le serveur — seule la reponse s'est perdue. Le client ne
 * peut pas distinguer ce cas d'un echec franc : il rejoue, et la visite est creee
 * deux fois. Aucune contrainte metier ne l'en empeche, deux visites du meme jour
 * etant legitimes ; seul le client sait que les deux requetes portent la MEME
 * intention, et il le dit par l'en-tete {@code Idempotency-Key}.
 *
 * <p>Trois cas, et un seul comportement pour chacun :
 * <ul>
 *   <li><strong>cle inconnue</strong> — la requete est traitee, puis sa reponse est
 *       memorisee (uniquement si elle a REUSSI : memoriser un echec figerait une
 *       erreur passagere en verdict definitif) ;
 *   <li><strong>cle connue, meme empreinte</strong> — c'est un rejeu : la reponse
 *       memorisee est renvoyee sans retraiter. Le client retrouve le meme
 *       identifiant de ressource qu'a la premiere fois ;
 *   <li><strong>cle connue, empreinte differente</strong> — deux operations
 *       distinctes se disputent la meme cle. C'est un bug client, pas un rejeu :
 *       409, plutot que de renvoyer la reponse d'une autre operation.
 * </ul>
 *
 * <p>Le filtre s'applique aux seules mutations, et seulement si l'en-tete est
 * present : un client qui ne le fournit pas garde le comportement anterieur, sans
 * cout ni surprise. Le stockage est en base, sous RLS (V14), et non en memoire :
 * une reponse memorisee doit survivre a un redemarrage — c'est justement
 * l'indisponibilite qui declenche les rejeux.
 */
public class FiltreIdempotence extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(FiltreIdempotence.class);

    public static final String EN_TETE = "Idempotency-Key";

    /** Bornee : la cle vient du client, elle ne doit pas pouvoir gonfler la table. */
    static final int LONGUEUR_MAX_CLE = 120;

    private final MagasinIdempotence magasin;

    public FiltreIdempotence(MagasinIdempotence magasin) {
        this.magasin = magasin;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requete) {
        String methode = requete.getMethod();
        boolean mutation = "POST".equals(methode) || "PUT".equals(methode) || "PATCH".equals(methode);
        return !mutation || requete.getHeader(EN_TETE) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse,
            FilterChain chaine) throws ServletException, IOException {

        String cle = requete.getHeader(EN_TETE);
        if (cle.isBlank() || cle.length() > LONGUEUR_MAX_CLE) {
            erreur(reponse, HttpStatus.BAD_REQUEST,
                    "En-tete Idempotency-Key vide ou trop long.");
            return;
        }

        // Le corps doit etre relu deux fois : une pour l'empreinte, une pour le
        // traitement. Un flux de servlet ne se lit qu'une fois, d'ou le cache.
        ContentCachingRequestWrapper requeteCachee = new ContentCachingRequestWrapper(requete);
        // Lecture complete du corps AVANT la chaine : sans cela, l'empreinte
        // serait calculee sur un cache encore vide.
        byte[] corps = requeteCachee.getInputStream().readAllBytes();
        String empreinte = empreinte(requete.getMethod(), requete.getRequestURI(), corps);

        Optional<MagasinIdempotence.ReponseMemorisee> memorisee = magasin.retrouver(cle);
        if (memorisee.isPresent()) {
            MagasinIdempotence.ReponseMemorisee m = memorisee.get();
            if (!m.empreinte().equals(empreinte)) {
                LOG.warn("Cle d'idempotence reutilisee pour une requete differente.");
                erreur(reponse, HttpStatus.CONFLICT,
                        "Cette cle d'idempotence a deja servi a une autre operation.");
                return;
            }
            rejouer(reponse, m);
            return;
        }

        ContentCachingResponseWrapper reponseCachee = new ContentCachingResponseWrapper(reponse);
        chaine.doFilter(new RequeteRejouable(requeteCachee, corps), reponseCachee);

        byte[] contenu = reponseCachee.getContentAsByteArray();
        int statut = reponseCachee.getStatus();
        reponseCachee.copyBodyToResponse();

        // Seules les reponses REUSSIES sont memorisees : une panne transitoire ne
        // doit pas se transformer en echec definitif au rejeu suivant.
        if (statut >= 200 && statut < 300) {
            magasin.memoriser(cle, empreinte, statut, new String(contenu, StandardCharsets.UTF_8));
        }
    }

    private void rejouer(HttpServletResponse reponse, MagasinIdempotence.ReponseMemorisee m)
            throws IOException {
        reponse.setStatus(m.statut());
        reponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
        reponse.setCharacterEncoding("UTF-8");
        // En-tete explicite : un client, un journal ou un test peut ainsi voir que
        // la reponse vient du magasin et non d'un nouveau traitement.
        reponse.setHeader("Idempotent-Replay", "true");
        if (m.corps() != null) {
            reponse.getWriter().write(m.corps());
        }
    }

    private void erreur(HttpServletResponse reponse, HttpStatus statut, String detail)
            throws IOException {
        reponse.setStatus(statut.value());
        reponse.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        reponse.setCharacterEncoding("UTF-8");
        reponse.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + statut.getReasonPhrase()
                + "\",\"status\":" + statut.value() + ",\"detail\":\"" + detail + "\"}");
    }

    /** SHA-256 de methode + chemin + corps : identifie l'operation, pas la requete. */
    static String empreinte(String methode, String chemin, byte[] corps) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(methode.getBytes(StandardCharsets.UTF_8));
            sha.update((byte) '\n');
            sha.update(chemin.getBytes(StandardCharsets.UTF_8));
            sha.update((byte) '\n');
            sha.update(corps);
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 est exige par la specification de la plateforme Java.
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    /**
     * Redonne a la chaine un corps lisible : le corps a deja ete consomme pour
     * calculer l'empreinte, et un flux de servlet ne se rembobine pas.
     */
    private static final class RequeteRejouable
            extends jakarta.servlet.http.HttpServletRequestWrapper {

        private final byte[] corps;

        RequeteRejouable(HttpServletRequest requete, byte[] corps) {
            super(requete);
            this.corps = corps;
        }

        @Override
        public jakarta.servlet.ServletInputStream getInputStream() {
            java.io.ByteArrayInputStream source = new java.io.ByteArrayInputStream(corps);
            return new jakarta.servlet.ServletInputStream() {
                @Override
                public int read() {
                    return source.read();
                }

                @Override
                public boolean isFinished() {
                    return source.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(jakarta.servlet.ReadListener listener) {
                    // Traitement synchrone : aucune lecture asynchrone a notifier.
                }
            };
        }

        @Override
        public java.io.BufferedReader getReader() {
            return new java.io.BufferedReader(
                    new java.io.InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
