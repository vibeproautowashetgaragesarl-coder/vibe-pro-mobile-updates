package com.vibepro.mobile
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.text.InputType
import android.widget.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue

import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import android.util.Base64
class MainActivity : Activity() {
private fun estAdministrateurPrincipal(
    utilisateur: FirebaseUser,
    resultat: (Boolean) -> Unit
) {
    FirebaseFirestore.getInstance()
        .collection("administrateur")
        .document(utilisateur.uid)
        .get()
        .addOnSuccessListener { document ->
            val autorise = document.exists()
                && document.getString("role") == "admin"
                && document.getBoolean("actif") == true
                && document.getBoolean("principal") == true

            resultat(autorise)
        }
        .addOnFailureListener {
            resultat(false)
        }
}

private fun estAdministrateurActif(
    utilisateur: FirebaseUser,
    resultat: (Boolean) -> Unit
) {
    FirebaseFirestore.getInstance()
        .collection("administrateur")
        .document(utilisateur.uid)
        .get()
        .addOnSuccessListener { document ->
            val autorise = document.exists()
                && document.getString("role") == "admin"
                && document.getBoolean("actif") == true

            resultat(autorise)
        }
        .addOnFailureListener {
            resultat(false)
        }
}


    private val photosDiagnostic = mutableListOf<Uri>()

    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()


override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    auth = FirebaseAuth.getInstance()

    val utilisateur = auth.currentUser

    if (utilisateur != null) {
        FirebaseFirestore.getInstance()
            .collection("administrateur")
            .document(utilisateur.uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()
                    && document.getString("role") == "admin"
                    && document.getBoolean("actif") == true
                ) {
                    afficherEspaceAdmin(utilisateur)
                } else {
                    afficherEspaceClient(utilisateur)
                }
            }
            .addOnFailureListener {
                afficherAccueil()
            }
    } else {
        afficherAccueil()
    }
}

private fun afficherEspaceAdmin(utilisateur: FirebaseUser) {
    val layout = disposition()

    layout.addView(titre("ESPACE ADMINISTRATEUR"))
    layout.addView(titre("Demandes d'abonnement"))

    estAdministrateurPrincipal(utilisateur) { principal ->
        if (principal) {
            val gestionAdmins = bouton("GESTION DES ADMINISTRATEURS")
            gestionAdmins.setOnClickListener {
                afficherGestionAdministrateurs(utilisateur)
            }
            layout.addView(gestionAdmins, 2)
        }
    }

    val message = TextView(this).apply {
        text = "Chargement des demandes..."
        textSize = 16f
        gravity = Gravity.CENTER
    }
    layout.addView(message)

    val retour = bouton("Déconnexion")
    retour.setOnClickListener {
        auth.signOut()
        afficherAccueil()
    }
    layout.addView(retour)

    setContentView(ScrollView(this).apply {
        addView(layout)
    })

    FirebaseFirestore.getInstance()
        .collectionGroup("demandes")
        .whereEqualTo("service", "Abonnement lavage")
        .get()
        .addOnSuccessListener { resultats ->

            layout.removeView(message)

            if (resultats.isEmpty) {
                val vide = TextView(this).apply {
                    text = "Aucune demande d'abonnement pour le moment."
                    textSize = 16f
                    gravity = Gravity.CENTER
                }
                layout.addView(vide, 1)

            } else {
                for (document in resultats.documents) {

                    val uidClient =
                        document.reference.parent.parent?.id ?: "Inconnu"

                    val passages = document.get("joursPassage")
                        as? List<Map<String, Any>>

                    val calendrierTexte =
                        if (!passages.isNullOrEmpty()) {
                            passages.mapIndexed { index, passage ->
                                """
                                Passage ${index + 1}
                                Date : ${passage["date"] ?: ""}
                                Heure : ${passage["heure"] ?: ""}
                                """.trimIndent()
                            }.joinToString("\n\n")
                        } else {
                            "Calendrier non encore envoyé par le client."
                        }

                    val informations = TextView(this).apply {
                        text = """
                            CLIENT : $uidClient
                            Véhicule : ${document.getString("typeVehicule") ?: ""}
                            Formule : ${document.getString("formule") ?: ""}
                            Prix : ${document.get("prix") ?: ""} FCFA
                            Statut : ${document.getString("statut") ?: ""}

                            CALENDRIER DES PASSAGES :
                            $calendrierTexte
                            ------------------------------
                        """.trimIndent()
                     textSize = 18f
setTextColor(Color.WHITE)
setPadding(20, 24, 20, 24)
setLineSpacing(8f, 1f)
background = android.graphics.drawable.GradientDrawable().apply {
    setColor(Color.rgb(35, 45, 65))
    cornerRadius = 18f
    setStroke(2, Color.rgb(190, 155, 75))
}
                    }
                    layout.addView(
    informations,
    LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        setMargins(12, 12, 12, 12)
    }
)

                    val actions = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                        setPadding(12, 8, 12, 16)
                    }

                    val accepter = bouton("ACCEPTER")
                    val refuser = bouton("REFUSER")

                    actions.addView(
                        accepter,
                        LinearLayout.LayoutParams(0, -2, 1f)
                    )
                    actions.addView(
                        refuser,
                        LinearLayout.LayoutParams(0, -2, 1f)
                    )

                    layout.addView(actions)

                    accepter.setOnClickListener {
                        document.reference.update(
                            "statut", "Accepté"
                        ).addOnSuccessListener {
                            Toast.makeText(
                                this,
                                "Abonnement accepté !",
                                Toast.LENGTH_LONG
                            ).show()
                            afficherEspaceAdmin(utilisateur)
                        }.addOnFailureListener { erreur ->
                            Toast.makeText(
                                this,
                                "Erreur : ${erreur.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

  refuser.setOnClickListener {

    val motif = EditText(this).apply {
        hint = "Expliquez la raison du refus"
        minLines = 3
        setPadding(20, 20, 20, 20)
        inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
    }

    android.app.AlertDialog.Builder(this)
        .setTitle("Motif du refus")
        .setMessage("Veuillez indiquer au client pourquoi sa demande est refusée.")
        .setView(motif)
        .setPositiveButton("CONFIRMER LE REFUS") { _, _ ->

            val raison = motif.text.toString().trim()

            if (raison.isBlank()) {
                Toast.makeText(
                    this,
                    "Le motif du refus est obligatoire.",
                    Toast.LENGTH_LONG
                ).show()
                return@setPositiveButton
            }

            document.reference.update(
                mapOf(
                    "statut" to "Refusé",
                    "motifRefus" to raison
                )
            ).addOnSuccessListener {
                Toast.makeText(
                    this,
                    "Demande refusée. Motif enregistré.",
                    Toast.LENGTH_LONG
                ).show()

                afficherEspaceAdmin(utilisateur)

            }.addOnFailureListener { erreur ->
                Toast.makeText(
                    this,
                    "Erreur : ${erreur.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        .setNegativeButton("ANNULER", null)
        .show()
}

                    if (!passages.isNullOrEmpty() &&
                        document.getString("statutCalendrier") != "Confirmé"
                    ) {
                        val confirmerCalendrier =
                            bouton("CONFIRMER LE CALENDRIER")

                        confirmerCalendrier.setOnClickListener {
                            document.reference.update(
                                mapOf(
                                    "statutCalendrier" to "Confirmé",
                                    "dateConfirmationCalendrier" to
                                        com.google.firebase.firestore.FieldValue.serverTimestamp()
                                )
                            ).addOnSuccessListener {
                                Toast.makeText(
                                    this,
                                    "Calendrier confirmé avec succès !",
                                    Toast.LENGTH_LONG
                                ).show()
                                afficherEspaceAdmin(utilisateur)
                            }.addOnFailureListener { erreur ->
                                Toast.makeText(
                                    this,
                                    "Erreur : ${erreur.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }

                        layout.addView(confirmerCalendrier)
                    }
                }
            }
        }
        .addOnFailureListener { erreur ->
            message.text = "Erreur de chargement : ${erreur.message}"
        }
}

    

private fun disposition(): LinearLayout {
    val photo = android.graphics.BitmapFactory.decodeResource(
        resources,
        R.drawable.logo_vibe_pro
    )

    val fondPhoto = android.graphics.drawable.BitmapDrawable(
        resources,
        photo
    ).apply {
        gravity = Gravity.FILL
    }

  val voileBleu = android.graphics.drawable.ColorDrawable(
    Color.argb(230, 10, 25, 50)
)

    val fondComplet = android.graphics.drawable.LayerDrawable(
        arrayOf(fondPhoto, voileBleu)
    )

    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.TOP
        setPadding(
            dp(16),
            dp(16),
            dp(16),
            dp(16)
        )
        background = fondComplet
    }
}


private fun rendreDefilant(
    contenu: LinearLayout
): ScrollView {

    return ScrollView(this).apply {
        isFillViewport = true
        clipToPadding = false
        addView(contenu)
    }
}



private fun dp(valeur: Int): Int {
    return (valeur * resources.displayMetrics.density).toInt()
}


private fun creerBarreNavigation(
    utilisateur: FirebaseUser
): LinearLayout {

    val barre = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setBackgroundColor(Color.rgb(10, 25, 50))
        setPadding(4, 10, 4, 10)
    }

    fun ajouterRubrique(
        symbole: String,
        nom: String,
        action: () -> Unit
    ) {
        val rubrique = TextView(this).apply {
            text = "$symbole\n$nom"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(2, 8, 2, 8)
            setOnClickListener { action() }
        }

        barre.addView(
            rubrique,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )
    }

    ajouterRubrique("⌂", "Accueil") {
        afficherEspaceClient(utilisateur)
    }

    ajouterRubrique("⚙", "Services") {
        afficherServices(utilisateur)
    }

    ajouterRubrique("＋", "Réserver") {
        Toast.makeText(
            this,
            "Espace réservation en préparation",
            Toast.LENGTH_SHORT
        ).show()
    }

    ajouterRubrique("♧", "Alertes") {
        afficherMesDemandesAbonnement(utilisateur)
    }

    ajouterRubrique("●", "Compte") {
        afficherVehicules(utilisateur)
    }

    return barre
}




    

private fun titre(texte: String): TextView {
    return TextView(this).apply {
        text = texte
        textSize = 25f
        setTextColor(Color.rgb(212, 175, 55))
        setTypeface(null, android.graphics.Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(12, 24, 12, 24)
        setLineSpacing(4f, 1.1f)
        setBackgroundColor(Color.TRANSPARENT)
        includeFontPadding = true
    }
}




private fun champ(texte: String, type: Int): EditText {
    return EditText(this).apply {
        hint = texte
        inputType = type
        textSize = 18f

        setTextColor(Color.rgb(10, 25, 50))
        setHintTextColor(Color.rgb(95, 95, 95))

        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.WHITE)
            setCornerRadius(dp(14).toFloat())
            setStroke(dp(2), Color.rgb(212, 175, 55))
        }

        setPadding(
            dp(18),
            dp(14),
            dp(18),
            dp(14)
        )

        minHeight = dp(64)
        minimumHeight = dp(64)
        gravity = Gravity.CENTER_VERTICAL
        includeFontPadding = true
        setSingleLine(true)
    }
}


private fun ajouterChampMotDePasse(
    layout: LinearLayout,
    champMotDePasse: EditText
) {
    champMotDePasse.transformationMethod =
        android.text.method.PasswordTransformationMethod.getInstance()

    val ligne = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    champMotDePasse.layoutParams = LinearLayout.LayoutParams(
        0,
        LinearLayout.LayoutParams.WRAP_CONTENT,
        1f
    )

    val oeil = TextView(this).apply {
        text = "👁"
        textSize = 25f
        gravity = Gravity.CENTER
        setPadding(15, 12, 15, 12)

        setOnClickListener {
            val estCache = champMotDePasse.transformationMethod is
                android.text.method.PasswordTransformationMethod

            val position = champMotDePasse.selectionStart

            champMotDePasse.transformationMethod =
                if (estCache) {
                    android.text.method.HideReturnsTransformationMethod.getInstance()
                } else {
                    android.text.method.PasswordTransformationMethod.getInstance()
                }

            champMotDePasse.setSelection(
                position.coerceIn(0, champMotDePasse.text.length)
            )

            text = if (estCache) "🙈" else "👁"
        }
    }

    ligne.addView(champMotDePasse)
    ligne.addView(oeil)
    layout.addView(ligne)
}



private fun afficherGestionAdministrateurs(
    utilisateur: FirebaseUser
) {
    estAdministrateurPrincipal(utilisateur) { principal ->

        if (!principal) {
            afficherEspaceAdmin(utilisateur)
            return@estAdministrateurPrincipal
        }

        val layout = disposition()

        layout.addView(
            titre("GESTION DES ADMINISTRATEURS")
        )

        layout.addView(
            titre("ADMINISTRATEUR PRINCIPAL")
        )

        val informations = TextView(this).apply {
            text = """
                Accès réservé à l'administrateur principal.

                Tu peux gérer les administrateurs
                de Vibe Pro Mobile.
            """.trimIndent()
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(16, 20, 16, 20)
        }

        layout.addView(informations)


        val retour = bouton("Retour à l'espace administrateur")
        retour.setOnClickListener {
            afficherEspaceAdmin(utilisateur)
        }

        layout.addView(retour)

        setContentView(
            ScrollView(this).apply {
                addView(layout)
            }
        )
    }
}
    

private fun bouton(texte: String): Button {
    return Button(this).apply {
        text = texte
        setTextColor(Color.rgb(10, 25, 50))
        textSize = 17f
        setTypeface(null, android.graphics.Typeface.BOLD)

        setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(
                Color.rgb(212, 175, 55)
            )
        )

        isAllCaps = false
        minHeight = 64
        minimumHeight = dp(64)
        setPadding(
            dp(16),
            dp(14),
            dp(16),
            dp(14)
        )

        gravity = Gravity.CENTER
        includeFontPadding = true
        elevation = dp(5).toFloat()
    }
}

private fun afficherAccueil() {
    val layout = disposition()

    layout.gravity = Gravity.TOP
    layout.orientation = LinearLayout.VERTICAL
    layout.setPadding(20, 25, 20, 20)

    
// Véritable logo de VIBE PRO
val logo = android.widget.ImageView(this).apply {
    setImageResource(
        R.drawable.logo_vibe_pro)
    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
    adjustViewBounds = true
}

val logoParams = LinearLayout.LayoutParams(260, 180)
logoParams.gravity = Gravity.CENTER
layout.addView(logo, logoParams)

    // Identité
    layout.addView(titre("VIBE PRO"))

    val sousTitre = TextView(this).apply {
        text = "AUTO WASH & GARAGE SARL"
        textSize = 14f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
    }
    layout.addView(sousTitre)

    // Message d'accueil
    layout.addView(TextView(this).apply {
        text = "Bienvenue dans votre espace de services"
        textSize = 17f
        setTypeface(null, android.graphics.Typeface.BOLD)
        setTextColor(Color.rgb(212, 175, 55))
        setPadding(0, 30, 0, 15)
    })

    // Accès client et administrateur
val espaceClient = bouton("ESPACE CLIENT")
val creerCompte = bouton("CRÉER UN COMPTE")
val espaceAdmin = bouton("ESPACE ADMINISTRATEUR")

    espaceClient.setOnClickListener {
        afficherFormulaire(false, false)
    }
creerCompte.setOnClickListener {
    afficherFormulaire(true, false)
}
    espaceAdmin.setOnClickListener {
        afficherFormulaire(false, true)
    }

    layout.addView(espaceClient)
layout.addView(creerCompte)
layout.addView(espaceAdmin)
    

    setContentView(rendreDefilant(layout))
}

    private fun emailTechnique(numero: String): String {
        val propre = numero.filter { it.isDigit() }
        return "$propre@clients.vibepro.app"
    }

   private fun afficherFormulaire(
    nouveau: Boolean,
    administrateur: Boolean
) {
    val layout = disposition()

    layout.addView(titre(
        if (nouveau) "Créer mon compte" else "Connexion"
    ))

    val nom = champ("Nom et prénom", InputType.TYPE_CLASS_TEXT)
val emailPersonnel = champ(
    "Adresse e-mail personnelle",
    InputType.TYPE_CLASS_TEXT or
        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
)
    val telephone = champ(
    when {
        administrateur && nouveau -> "Adresse Gmail administrateur"
        administrateur -> "Adresse Gmail ou téléphone associé"
        nouveau -> "Numéro de téléphone (+226)"
        else -> "Adresse Gmail ou téléphone"
    },
    InputType.TYPE_CLASS_TEXT
)

    val motDePasse = champ(
        "Mot de passe",
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    )

    if (nouveau) layout.addView(nom)
layout.addView(telephone)

if (nouveau && !administrateur) {
    layout.addView(emailPersonnel)
}
    ajouterChampMotDePasse(layout, motDePasse)

if (!nouveau && !administrateur) {
    val motDePasseOublie = TextView(this).apply {
        text = "Mot de passe oublié ?"
        textSize = 15f
        setTextColor(Color.rgb(212, 175, 55))
        gravity = Gravity.END
        setPadding(0, 12, 5, 12)

        setOnClickListener {
            val adresse = telephone.text.toString().trim()

            if (!adresse.contains("@")) {
                Toast.makeText(
                    this@MainActivity,
                    "Saisissez votre adresse e-mail personnelle.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                auth.sendPasswordResetEmail(adresse)
                    .addOnSuccessListener {
                        Toast.makeText(
                            this@MainActivity,
                            "Lien envoyé. Consultez votre boîte e-mail.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(
                            this@MainActivity,
                            "Impossible d'envoyer le lien. Vérifiez votre adresse.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
        }
    }

    layout.addView(motDePasseOublie)
}


    val message = TextView(this).apply {
        gravity = Gravity.CENTER
        setTextColor(Color.RED)
        textSize = 14f
    }

    val valider = bouton(
        if (nouveau) "Créer mon compte" else "Se connecter"
    )
    val retour = bouton("Retour")

    val action = {
        val identifiant = telephone.text.toString().trim()
val emailSaisi = emailPersonnel.text.toString().trim()
val mdp = motDePasse.text.toString()
val nomSaisi = nom.text.toString().trim()

        val estEmail = identifiant.contains("@")
val numeroValide = identifiant
    .filter { it.isDigit() }
    .let { it.length == 8 || it.length == 11 }

    val numero = if (identifiant.startsWith("+")) {
    identifiant
} else {
    "+226$identifiant"
}

val email = when {
    nouveau && !administrateur -> emailSaisi
    estEmail -> identifiant
    else -> emailTechnique(numero)
}

if (nouveau && nomSaisi.isBlank()) {
    message.text = "Entre ton nom et prénom."
} else if (identifiant.isBlank()) {
    message.text = "Entre ton téléphone ou ton adresse Gmail."
} else if (administrateur && !estEmail) {
    message.text = "Les administrateurs doivent utiliser leur adresse e-mail."
} else if (nouveau && !administrateur && !numeroValide) {
    message.text = "Entre un numéro de téléphone valide."
} else if (
    nouveau && !administrateur &&
    !android.util.Patterns.EMAIL_ADDRESS.matcher(emailSaisi).matches()
) {
    message.text = "Entre une adresse e-mail valide."
} else if (mdp.length < 6) {
    message.text = "Le mot de passe doit avoir au moins 6 caractères."
} else {
    val operation = if (nouveau) {
        auth.createUserWithEmailAndPassword(email, mdp)
    } else {
        auth.signInWithEmailAndPassword(email, mdp)
    }

            val boutonActuel = valider
            boutonActuel.isEnabled = false
            message.setTextColor(Color.DKGRAY)
            message.text = "Patiente un instant..."


operation.addOnCompleteListener(this) { resultat ->

    if (resultat.isSuccessful) {

        val utilisateur = resultat.result.user

        if (utilisateur == null) {

            boutonActuel.isEnabled = true
            message.text = "Impossible de retrouver le compte."

        } else if (nouveau) {

            val tel = identifiant
            val numero = if (tel.startsWith("+")) tel else "+226$tel"

            enregistrerProfil(
                utilisateur,
                nomSaisi,
                numero,
                emailSaisi,
                message,
                boutonActuel
            )

        } else if (administrateur) {

            if (utilisateur.uid == "XYNQbvoFuDclpbVWFhxPOLnFYKu1") {
                afficherEspaceAdmin(utilisateur)
            } else {
                auth.signOut()
                boutonActuel.isEnabled = true
                message.setTextColor(Color.RED)
                message.text = "Accès refusé : administrateur non autorisé."
            }

        } else {
            afficherEspaceClient(utilisateur)
        }

    } else {
        boutonActuel.isEnabled = true
        message.setTextColor(Color.RED)
        message.text = "Erreur : vérifie ton identifiant et ton mot de passe."
    }
}
   }
  }


    valider.setOnClickListener { action() }
    retour.setOnClickListener { afficherAccueil() }

    layout.addView(valider)
    layout.addView(message)
    layout.addView(retour)

    setContentView(rendreDefilant(layout))
}

    private fun enregistrerProfil(
        utilisateur: FirebaseUser,
        nom: String,
telephone: String,
emailPersonnel: String,
message: TextView,
        bouton: Button
    ) {
        val profil = hashMapOf(
            "uid" to utilisateur.uid,
            "nom" to nom,
            "telephone" to telephone,
"emailPersonnel" to emailPersonnel,
"emailTechnique" to utilisateur.email,
            "dateCreation" to FieldValue.serverTimestamp()
        )

        db.collection("clients")
            .document(utilisateur.uid)
            .set(profil)
            .addOnSuccessListener {
                afficherEspaceClient(utilisateur)
            }
            .addOnFailureListener {
                bouton.isEnabled = true
                message.setTextColor(Color.RED)
                message.text =
                    "Compte créé, mais profil non enregistré. Vérifie Firestore."
            }
    }


private fun afficherEspaceClient(utilisateur: FirebaseUser) {
    val layout = disposition()

    layout.setPadding(16, 20, 16, 20)

    layout.addView(titre("VIBE PRO MOBILE"))

    val nomClient = TextView(this).apply {
        text = "Bienvenue dans votre espace client !"
        textSize = 21f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setPadding(8, 20, 8, 8)
    }
    layout.addView(nomClient)

    db.collection("clients")
        .document(utilisateur.uid)
        .get()
        .addOnSuccessListener { document ->
            val nom = document.getString("nom")
            if (!nom.isNullOrBlank()) {
                nomClient.text = "Bonjour, $nom !"
            }
        }

    val presentation = TextView(this).apply {
        text = "Bienvenue chez Vibe Pro. Retrouvez tous nos services et gérez vos prestations en quelques clics."
        textSize = 14f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setPadding(12, 8, 12, 20)
    }
    layout.addView(presentation)

    val compte = TextView(this).apply {
        text = "Connecté : ${utilisateur.email ?: "Client Vibe Pro"}"
        textSize = 12f
        setTextColor(Color.LTGRAY)
        gravity = Gravity.CENTER
        setPadding(8, 8, 8, 18)
    }
    layout.addView(compte)

    val espace = TextView(this).apply {
        text = "MES SERVICES"
        textSize = 17f
        setTextColor(Color.YELLOW)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(8, 16, 8, 12)
    }
    layout.addView(espace)

    val services = bouton("🚘  Découvrir nos services")
    services.setOnClickListener {
        afficherServices(utilisateur)
    }
    layout.addView(services)

    val abonnements = bouton("⭐  Mes abonnements")
    abonnements.setOnClickListener {
        afficherAbonnements(utilisateur)
    }
    layout.addView(abonnements)

    val suiviAbonnements = bouton("📋  Suivre mes demandes")
    suiviAbonnements.setOnClickListener {
        afficherMesDemandesAbonnement(utilisateur)
    }
    layout.addView(suiviAbonnements)
val calendrierPassage = bouton("📅  MON CALENDRIER DE PASSAGE")
calendrierPassage.setOnClickListener {
    afficherMonCalendrierPassage(utilisateur)
}
layout.addView(calendrierPassage)

    val mesVehicules = bouton("🚗  Mes véhicules")
    mesVehicules.setOnClickListener {
        afficherVehicules(utilisateur)
    }
    layout.addView(mesVehicules)

    val deconnexion = bouton("Se déconnecter")
    deconnexion.setOnClickListener {
        auth.signOut()
        afficherAccueil()
    }

    
    layout.addView(deconnexion)

    layout.addView(
        creerBarreNavigation(utilisateur),
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    )

    setContentView(rendreDefilant(layout))
}

    private fun afficherVehicules(utilisateur: FirebaseUser) {
        val layout = disposition()
        layout.addView(titre("MES VÉHICULES"))

        val message = TextView(this).apply {
            text = "Chargement des véhicules..."
            textSize = 15f
            gravity = Gravity.CENTER
        }
        layout.addView(message)

        val ajouter = bouton("Ajouter un véhicule")
        ajouter.setOnClickListener {
            afficherFormulaireVehicule(utilisateur)
        }

        val retour = bouton("Retour à mon espace")
        retour.setOnClickListener {
            afficherEspaceClient(utilisateur)
        }

        layout.addView(ajouter)
        layout.addView(retour)

        setContentView(ScrollView(this).apply {
            addView(layout)
        })

        db.collection("clients")
            .document(utilisateur.uid)
            .collection("vehicules")
            .get()
            .addOnSuccessListener { documents ->
                message.text = if (documents.isEmpty) {
                    "Aucun véhicule enregistré pour le moment."
                } else {
                    "Mes véhicules :"
                }

                for (document in documents) {
                    val marque = document.getString("marque") ?: ""
                    val modele = document.getString("modele") ?: ""
                    val immatriculation =
                        document.getString("immatriculation") ?: ""
                    val annee = document.getString("annee") ?: ""
                    val type = document.getString("type") ?: ""

                    val details = TextView(this).apply {
                        text = "$marque $modele\nImmatriculation : $immatriculation\nAnnée : $annee\nType : $type"
                        textSize = 16f
                        setTextColor(Color.rgb(20, 40, 80))
                        setPadding(10, 20, 10, 20)
                    }
                    layout.addView(details)
                }
            }
            .addOnFailureListener {
                message.text = "Impossible de charger les véhicules."
            }
    }

    private fun afficherFormulaireVehicule(utilisateur: FirebaseUser) {
        val layout = disposition()
        layout.addView(titre("AJOUTER UN VÉHICULE"))

        val marque = champ("Marque (Toyota, Nissan...)", InputType.TYPE_CLASS_TEXT)
        val modele = champ("Modèle", InputType.TYPE_CLASS_TEXT)
        val immatriculation = champ("Immatriculation", InputType.TYPE_CLASS_TEXT)
        val annee = champ("Année", InputType.TYPE_CLASS_NUMBER)

        val type = Spinner(this)
        val types = arrayOf("Voiture", "Moto", "Camionnette", "Camion", "Autre")
        type.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            types
        )

        layout.addView(marque)
        layout.addView(modele)
        layout.addView(immatriculation)
        layout.addView(annee)
        layout.addView(type)

        val message = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.RED)
            textSize = 14f
        }

        val enregistrer = bouton("Enregistrer le véhicule")
        enregistrer.setOnClickListener {
            val marqueTexte = marque.text.toString().trim()
            val modeleTexte = modele.text.toString().trim()
            val plaque = immatriculation.text.toString().trim()
            val anneeTexte = annee.text.toString().trim()

            if (marqueTexte.isBlank() || modeleTexte.isBlank() ||
                plaque.isBlank() || anneeTexte.isBlank()
            ) {
                message.text = "Remplis tous les champs."
            } else {
                enregistrer.isEnabled = false
                message.setTextColor(Color.DKGRAY)
                message.text = "Enregistrement en cours..."

                val vehicule = hashMapOf(
                    "marque" to marqueTexte,
                    "modele" to modeleTexte,
                    "immatriculation" to plaque,
                    "annee" to anneeTexte,
                    "type" to type.selectedItem.toString(),
                    "dateAjout" to FieldValue.serverTimestamp()
                )

                db.collection("clients")
                    .document(utilisateur.uid)
                    .collection("vehicules")
                    .add(vehicule)
                    .addOnSuccessListener {
                        afficherVehicules(utilisateur)
                    }
                    .addOnFailureListener {
                        enregistrer.isEnabled = true
                        message.setTextColor(Color.RED)
                        message.text =
                            "Échec de l'enregistrement. Vérifie les règles Firestore."
                    }
            }
        }

        val retour = bouton("Retour")
        retour.setOnClickListener {
            afficherVehicules(utilisateur)
        }

        layout.addView(enregistrer)
        layout.addView(message)
        layout.addView(retour)

        setContentView(ScrollView(this).apply {
            addView(layout)
        })
    }
     
   
private fun afficherServices(utilisateur: FirebaseUser) {
    val racine = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }

    val contenu = disposition()

    contenu.addView(titre("NOS SERVICES"))

    contenu.addView(TextView(this).apply {
        text = "Découvrez toutes les activités de Vibe Pro Auto Wash & Garage SARL."
        textSize = 16f
        gravity = Gravity.CENTER
        setPadding(12, 12, 12, 20)
    })

    val grille = creerGrille()

    ajouterBoutonEnGrille(
        grille,
        "LAVAGE PONCTUEL"
    ) {
        afficherChoixVehiculeLavagePonctuel(utilisateur)
    }

    val listeServices = listOf(
        "Nettoyage industriel et immeubles",
        "Mécanique générale auto-moto",
        "Diagnostic automobile",
        "Diagnostic visuel",
        "Entretien et réparation",
        "Produits et accessoires auto",
        "Dépannage léger",
        "Équipements de lavage",
        "Autres activités"
    )

    for (service in listeServices) {
        ajouterBoutonEnGrille(grille, service) {
            when (service) {
                "Diagnostic automobile" -> {
                    afficherFormulaireDiagnostic(utilisateur)
                }

                "Diagnostic visuel" -> {
                    afficherFormulaireDiagnosticVisuel(utilisateur)
                }

                else -> {
                    Toast.makeText(
                        this,
                        service,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    contenu.addView(grille)

    val defilement = android.widget.ScrollView(this).apply {
        isFillViewport = true
        addView(contenu)
    }

    racine.addView(
        defilement,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
    )

    val retour = bouton("RETOUR À MON ESPACE")
    retour.setOnClickListener {
        afficherEspaceClient(utilisateur)
    }

    racine.addView(
        retour,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(12, 8, 12, 12)
        }
    )

    setContentView(racine)
}


private fun afficherAbonnements(utilisateur: FirebaseUser) {
    val layout = disposition()

    layout.addView(titre("MES ABONNEMENTS"))

    layout.addView(TextView(this).apply {
        text = "Découvrez nos formules d'abonnement et bénéficiez d'un suivi régulier de vos prestations."
        textSize = 16f
        gravity = Gravity.CENTER
        setPadding(12, 12, 12, 20)
    })

    val categories = listOf(
        "Lavage automobile et moto",
        "Nettoyage maisons, bureaux et immeubles",
        "Entretien automobile et moto",
        "Entretien camions et bus"
    )

    for (categorie in categories) {
        val choix = bouton(categorie)

       choix.setOnClickListener {
    if (categorie == "Lavage automobile et moto") {
        afficherFormulesLavage(utilisateur)
    } else {
        Toast.makeText(
            this,
            "Cette catégorie sera bientôt disponible.",
            Toast.LENGTH_SHORT
        ).show()
    }
}

        layout.addView(choix)
    }

    val retour = bouton("Retour à mon espace")

    retour.setOnClickListener {
        afficherEspaceClient(utilisateur)
    }

    layout.addView(retour)

    setContentView(layout)
}
private fun afficherFormulesLavage(utilisateur: FirebaseUser) {
    val layout = disposition()

    layout.addView(titre("ABONNEMENT LAVAGE"))

    layout.addView(TextView(this).apply {
        text = "Choisissez le type de véhicule à abonner."
        textSize = 16f
        gravity = Gravity.CENTER
        setPadding(12, 12, 12, 20)
    })

    val automobile = bouton("Lavage automobile")
    automobile.setOnClickListener {
        afficherChoixFormulesLavage(utilisateur, "Automobile")
    }
    layout.addView(automobile)

    val moto = bouton("Lavage moto")
    moto.setOnClickListener {
        afficherChoixFormulesLavage(utilisateur, "Moto")
    }
    layout.addView(moto)
val camion = bouton("Lavage camion")
camion.setOnClickListener {
    afficherChoixFormulesLavage(utilisateur, "Camion")
}
layout.addView(camion)

    val retour = bouton("Retour")
    retour.setOnClickListener {
        afficherAbonnements(utilisateur)
    }
    layout.addView(retour)

    setContentView(layout)
}
private fun afficherChoixFormulesLavage(
    utilisateur: FirebaseUser,
    typeVehicule: String
) {
    val layout = disposition()

    layout.addView(titre("NOS FORMULES $typeVehicule"))

    layout.addView(TextView(this).apply {
        text = "Abonnement mensuel : 4 lavages."
        textSize = 16f
        gravity = Gravity.CENTER
        setPadding(12, 12, 12, 20)
    })

    val formules = if (typeVehicule == "Automobile") {
        listOf(
            "Basic" to 3500,
            "Standard" to 5500,
            "Premium" to 7500,
            "VIP Premium" to 13500
        )
    } else {
        listOf(
            "Formule Moto 1" to 1000,
            "Formule Moto 2" to 1500,
            "Formule Moto 3" to 2000,
            "Formule Moto 4" to 3500,
            "Formule Moto 5" to 5000
        )
    }

    for ((nom, prix) in formules) {
        val choix = bouton("$nom — $prix FCFA / mois")

        choix.setOnClickListener {
    afficherConfirmationAbonnement(
        utilisateur,
        typeVehicule,
        nom,
        prix
    )
}

        layout.addView(choix)
    }

    val retour = bouton("Retour")
    retour.setOnClickListener {
        afficherFormulesLavage(utilisateur)
    }
    layout.addView(retour)

    setContentView(layout)
}
private fun afficherConfirmationAbonnement(
    utilisateur: FirebaseUser,
    typeVehicule: String,
    formule: String,
    prix: Int
) {
    val layout = disposition()

    layout.addView(titre("CONFIRMATION ABONNEMENT"))

    layout.addView(TextView(this).apply {
        text = """
            Type : $typeVehicule
            Formule : $formule
            Durée : 1 mois
            Nombre de lavages : 4
            Montant : $prix FCFA
        """.trimIndent()
        textSize = 18f
        setPadding(16, 20, 16, 20)
    })

    layout.addView(titre("PAIEMENT MOBILE MONEY"))

    layout.addView(TextView(this).apply {
        text = """
            Moov Money : 61 93 24 24
            Telecel Money : 58 01 01 21
            Orange Money : 66 35 49 07

            Après paiement, cliquez sur le bouton ci-dessous pour envoyer votre demande.
        """.trimIndent()
        textSize = 16f
        setPadding(16, 12, 16, 20)
    })

    val confirmer = bouton("Confirmer ma demande")

    confirmer.setOnClickListener {

    val demande = hashMapOf<String, Any>(
        "uid" to utilisateur.uid,
        "service" to "Abonnement lavage",
        "typeVehicule" to typeVehicule,
        "formule" to formule,
        "prix" to prix,
        "nombreLavages" to 4,
        "duree" to "1 mois",
        "statut" to "En attente de validation",
        "dateDemande" to com.google.firebase.firestore.FieldValue.serverTimestamp()
    )

    FirebaseFirestore.getInstance()
        .collection("clients")
        .document(utilisateur.uid)
        .collection("demandes")
        .add(demande)
        .addOnSuccessListener {
            Toast.makeText(
                this,
                "Votre demande d'abonnement a été envoyée avec succès.",
                Toast.LENGTH_LONG
            ).show()

            afficherEspaceClient(utilisateur)
        }
        .addOnFailureListener { erreur ->
            Toast.makeText(
                this,
                "Erreur : ${erreur.message}",
                Toast.LENGTH_LONG
            ).show()
        }
}

    layout.addView(confirmer)

    val retour = bouton("Retour")
    retour.setOnClickListener {
        afficherChoixFormulesLavage(utilisateur, typeVehicule)
    }
    layout.addView(retour)

    setContentView(layout)
}
private fun afficherMesDemandesAbonnement(utilisateur: FirebaseUser) {
    val layout = disposition()

    layout.addView(titre("MES DEMANDES D'ABONNEMENT"))

    val chargement = TextView(this).apply {
        text = "Chargement de vos demandes..."
        textSize = 16f
        gravity = Gravity.CENTER
        setPadding(12, 20, 12, 20)
    }

    layout.addView(chargement)

    FirebaseFirestore.getInstance()
        .collection("clients")
        .document(utilisateur.uid)
        .collection("demandes")
        .whereEqualTo("service", "Abonnement lavage")
        .get()
        .addOnSuccessListener { resultat ->

            layout.removeView(chargement)

            if (resultat.isEmpty) {
                layout.addView(TextView(this).apply {
                    text = "Vous n'avez aucune demande d'abonnement."
                    textSize = 16f
                    gravity = Gravity.CENTER
                    setPadding(12, 20, 12, 20)
                })
            } else {
                for (document in resultat.documents) {

                    val type = document.getString("typeVehicule") ?: ""
                    val formule = document.getString("formule") ?: ""
                    val prix = document.getLong("prix") ?: 0
                    val statut = document.getString("statut") ?: "En attente"

                    val informations = TextView(this).apply {
    text = """
        VOTRE ABONNEMENT

        Véhicule : $type
        Formule : $formule
        Prix : $prix FCFA

        Statut : $statut
    """.trimIndent()

    textSize = 18f
    setTextColor(Color.WHITE)
    setTypeface(null, android.graphics.Typeface.BOLD)

    setPadding(
        dp(20),
        dp(20),
        dp(20),
        dp(20)
    )

    setLineSpacing(dp(6).toFloat(), 1f)

    background = android.graphics.drawable.GradientDrawable().apply {
        setColor(Color.rgb(35, 50, 75))
        cornerRadius = dp(16).toFloat()
        setStroke(dp(2), Color.rgb(212, 175, 55))
    }
}

layout.addView(
    informations,
    LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        setMargins(dp(8), dp(10), dp(8), dp(10))
    }
)
                }
            }
        }
        .addOnFailureListener { erreur ->
            chargement.text = "Erreur de chargement : ${erreur.message}"
        }

    val retour = bouton("Retour à mon espace")
    retour.setOnClickListener {
        afficherEspaceClient(utilisateur)
    }
    layout.addView(retour)

    setContentView(layout)
}
     private fun afficherFormulaireDiagnostic(utilisateur: FirebaseUser) {
    val layout = disposition()

    layout.addView(titre("DEMANDE DE DIAGNOSTIC"))

    val marque = champ("Marque du véhicule", InputType.TYPE_CLASS_TEXT)
    val modele = champ("Modèle du véhicule", InputType.TYPE_CLASS_TEXT)
    val annee = champ("Année du véhicule", InputType.TYPE_CLASS_NUMBER)
    val moteur = champ("Motorisation (ex : 2.0)", InputType.TYPE_CLASS_TEXT)
    val carburant = champ("Carburant (essence, diesel...)", InputType.TYPE_CLASS_TEXT)
    val immatriculation = champ("Immatriculation", InputType.TYPE_CLASS_TEXT)
    val kilometrage = champ("Kilométrage", InputType.TYPE_CLASS_NUMBER)
    val description = champ("Décrivez la panne ou le problème", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)

    val envoyer = bouton("Envoyer ma demande")
    val retour = bouton("Retour aux services")

    envoyer.setOnClickListener {
        if (
            marque.text.isBlank() ||
            modele.text.isBlank() ||
            description.text.isBlank()
        ) {
            Toast.makeText(
                this,
                "Veuillez remplir la marque, le modèle et la description.",
                Toast.LENGTH_LONG
            ).show()
            return@setOnClickListener
        }

        val demande = hashMapOf<String, Any>(
            "uid" to utilisateur.uid,
            "service" to "Diagnostic automobile",
            "marque" to marque.text.toString().trim(),
            "modele" to modele.text.toString().trim(),
            "annee" to annee.text.toString().trim(),
            "moteur" to moteur.text.toString().trim(),
            "carburant" to carburant.text.toString().trim(),
            "immatriculation" to immatriculation.text.toString().trim(),
            "kilometrage" to kilometrage.text.toString().trim(),
            "description" to description.text.toString().trim(),
            "statut" to "Nouvelle demande",
            "dateCreation" to FieldValue.serverTimestamp()
        )

        FirebaseFirestore.getInstance()
            .collection("clients")
            .document(utilisateur.uid)
            .collection("demandes")
            .add(demande)
            .addOnSuccessListener {
                Toast.makeText(
                    this,
                    "Votre demande a été envoyée avec succès.",
                    Toast.LENGTH_LONG
                ).show()
                afficherEspaceClient(utilisateur)
            }
            .addOnFailureListener { erreur ->
                Toast.makeText(
                    this,
                    "Échec de l'envoi : ${erreur.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    retour.setOnClickListener {
        afficherServices(utilisateur)
    }

    layout.addView(marque)
    layout.addView(modele)
    layout.addView(annee)
    layout.addView(moteur)
    layout.addView(carburant)
    layout.addView(immatriculation)
    layout.addView(kilometrage)
    layout.addView(description)
    layout.addView(envoyer)
    layout.addView(retour)

    setContentView(layout)
}

private fun afficherFormulaireDiagnosticVisuel(utilisateur: FirebaseUser) {
    val layout = disposition()

    layout.addView(titre("DIAGNOSTIC VISUEL"))

    val marque = champ("Marque du véhicule", InputType.TYPE_CLASS_TEXT)
    val modele = champ("Modèle du véhicule", InputType.TYPE_CLASS_TEXT)
    val immatriculation = champ("Immatriculation", InputType.TYPE_CLASS_TEXT)
    val symptomes = champ(
        "Décrivez les anomalies constatées",
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
    )
   val ajouterPhotos = bouton("Ajouter des photos (maximum 3)")

ajouterPhotos.setOnClickListener {
    ouvrirGalerieDiagnostic()
}
    val envoyer = bouton("Envoyer ma demande")
    val retour = bouton("Retour aux services")

    envoyer.setOnClickListener {
        if (
            marque.text.isBlank() ||
            modele.text.isBlank() ||
            symptomes.text.isBlank()
        ) {
            Toast.makeText(
                this,
                "Veuillez remplir la marque, le modèle et les anomalies.",
                Toast.LENGTH_LONG
            ).show()
            return@setOnClickListener
        }
val photosBase64 = try {
    photosDiagnostic.map {
        convertirPhotoEnBase64(it)
    }
} catch (erreur: Exception) {
    Toast.makeText(
        this,
        "Erreur avec une photo : ${erreur.message}",
        Toast.LENGTH_LONG
    ).show()
    return@setOnClickListener
}
val demande = hashMapOf<String, Any>(
    "uid" to utilisateur.uid,
    "service" to "Diagnostic visuel",
    "marque" to marque.text.toString().trim(),
    "modele" to modele.text.toString().trim(),
    "immatriculation" to immatriculation.text.toString().trim(),
    "symptomes" to symptomes.text.toString().trim(),
  "photos" to photosBase64,
    "statut" to "Nouvelle demande",
    "dateCreation" to FieldValue.serverTimestamp()
)

        FirebaseFirestore.getInstance()
            .collection("clients")
            .document(utilisateur.uid)
            .collection("demandes")
            .add(demande)
            .addOnSuccessListener {
                Toast.makeText(
                    this,
                    "Votre demande de diagnostic visuel a été envoyée.",
                    Toast.LENGTH_LONG
                ).show()
                afficherEspaceClient(utilisateur)
            }
            .addOnFailureListener { erreur ->
                Toast.makeText(
                    this,
                    "Échec de l'envoi : ${erreur.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    retour.setOnClickListener {
        afficherServices(utilisateur)
    }

    layout.addView(marque)
    layout.addView(modele)
    layout.addView(immatriculation)
    layout.addView(symptomes)
    layout.addView(ajouterPhotos)
    layout.addView(envoyer)
    layout.addView(retour)

    setContentView(layout)
}
private fun ouvrirGalerieDiagnostic() {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        type = "image/*"
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        addCategory(Intent.CATEGORY_OPENABLE)
    }

    startActivityForResult(intent, 1001)
}


override fun onActivityResult(
    requestCode: Int,
    resultCode: Int,
    data: Intent?
) {
    super.onActivityResult(requestCode, resultCode, data)

    if (requestCode == 1001 && resultCode == RESULT_OK) {

        photosDiagnostic.clear()

        if (data?.clipData != null) {
            val images = data.clipData!!

            for (i in 0 until minOf(images.itemCount, 3)) {
                val uri = images.getItemAt(i).uri

                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Autorisation déjà accordée ou non persistante.
                }

                photosDiagnostic.add(uri)
            }

        } else if (data?.data != null) {
            val uri = data.data!!

            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Autorisation déjà accordée ou non persistante.
            }

            photosDiagnostic.add(uri)
        }

        Toast.makeText(
            this,
            "${photosDiagnostic.size} photo(s) sélectionnée(s)",
            Toast.LENGTH_LONG
        ).show()
    }
}

private fun convertirPhotoEnBase64(uri: Uri): String {

    val donnees = contentResolver.openInputStream(uri)?.use {
        it.readBytes()
    } ?: throw Exception("Impossible d'ouvrir la photo")

    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }

    BitmapFactory.decodeByteArray(
        donnees, 0, donnees.size, options
    )

    if (options.outWidth <= 0 || options.outHeight <= 0) {
        throw Exception("Format de photo non reconnu")
    }

    val tailleMax = maxOf(options.outWidth, options.outHeight)

    val echantillon = maxOf(
        1,
        (tailleMax + 799) / 800
    )

    val bitmapOptions = BitmapFactory.Options().apply {
        inSampleSize = echantillon
    }

    val bitmap = BitmapFactory.decodeByteArray(
        donnees, 0, donnees.size, bitmapOptions
    ) ?: throw Exception("Impossible de compresser la photo")

    val sortie = ByteArrayOutputStream()

    bitmap.compress(Bitmap.CompressFormat.JPEG, 45, sortie)
    bitmap.recycle()

    if (sortie.size() > 150 * 1024) {
        throw Exception("Photo trop volumineuse. Choisissez une autre photo.")
    }

    return Base64.encodeToString(
        sortie.toByteArray(),
        Base64.NO_WRAP
    )
}


private fun verifierAbonnementAccepte(
    utilisateur: FirebaseUser
) {
    db.collection("clients")
        .document(utilisateur.uid)
        .collection("demandes")
        .get()
        .addOnSuccessListener { resultats ->

            val demande = resultats.documents
                .lastOrNull {
                    it.getString("service") == "Abonnement lavage"
                }

            val layout = disposition()

            layout.addView(titre("SUIVI DE MON ABONNEMENT"))

            if (demande == null) {

                layout.addView(TextView(this).apply {
                    text = "Vous n'avez aucune demande d'abonnement pour le moment."
                    textSize = 16f
                    gravity = Gravity.CENTER
                    setPadding(12, 25, 12, 25)
                    setTextColor(Color.BLACK)
                })

            } else {

                val statut = demande.getString("statut") ?: "En attente"
                val statutCalendrier =
                    demande.getString("statutCalendrier") ?: ""

                layout.addView(TextView(this).apply {
                    text = "Formule : ${demande.getString("formule") ?: "Non précisée"}\n" +
                           "Véhicule : ${demande.getString("typeVehicule") ?: "Non précisé"}"
                    textSize = 17f
                    setTextColor(Color.BLACK)
                    setPadding(12, 20, 12, 20)
                })

                val message = when (statut) {
                    "Accepté" -> {
                        if (statutCalendrier == "Confirmé") {
                            "Votre abonnement est accepté et votre calendrier est confirmé."
                        } else {
                            "Bonne nouvelle ! Votre abonnement est accepté. Vous pouvez maintenant programmer vos quatre passages."
                        }
                    }

                    "Refusé" -> {
                        "Votre demande d'abonnement n'a pas été acceptée. Contactez Vibe Pro pour plus d'informations."
                    }

                    else -> {
                        "Votre demande est en attente de validation par Vibe Pro. Nous vous informerons dès qu'une décision sera prise."
                    }
                }

                layout.addView(TextView(this).apply {
                    text = message
                    textSize = 16f
                    gravity = Gravity.CENTER
                    setTextColor(Color.BLACK)
                    setPadding(16, 20, 16, 25)
                })

                if (statut == "Accepté" &&
                    statutCalendrier != "Confirmé"
                ) {
                    val programmer = bouton("PROGRAMMER MES 4 PASSAGES")

                    programmer.setOnClickListener {
                        afficherFormulaireProgrammation(
                            utilisateur,
                            demande.id,
                            demande.getString("typeVehicule") ?: "",
                            demande.getString("formule") ?: ""
                        )
                    }

                    layout.addView(programmer)
                }
            }

            val retour = bouton("RETOUR À MON ESPACE")
            retour.setOnClickListener {
                afficherEspaceClient(utilisateur)
            }

            layout.addView(retour)

            setContentView(rendreDefilant(layout))
        }
        .addOnFailureListener { erreur ->

            Toast.makeText(
                this,
                "Erreur de vérification : ${erreur.message}",
                Toast.LENGTH_LONG
            ).show()
        }
}



private fun afficherFormulaireProgrammation(
    utilisateur: FirebaseUser,
    demandeId: String,
    typeVehicule: String,
    formule: String
) {
    val layout = disposition()

    layout.addView(titre("PROGRAMMER MES PASSAGES"))

    val introduction = TextView(this).apply {
        text = """
            Félicitations ! Votre abonnement est accepté.

            Véhicule : $typeVehicule
            Formule : $formule

            Sélectionnez les quatre dates et heures souhaitées.
        """.trimIndent()
        textSize = 16f
        setTextColor(Color.BLACK)
        setPadding(dp(16), dp(18), dp(16), dp(18))
        background = GradientDrawable().apply {
            setColor(Color.rgb(245, 248, 252))
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), Color.rgb(212, 175, 55))
        }
    }

    layout.addView(introduction)

    val dates = MutableList(4) { "" }
    val heures = MutableList(4) { "" }

    val boutonsDates = mutableListOf<TextView>()
    val boutonsHeures = mutableListOf<TextView>()

    for (i in 0 until 4) {

        val carte = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.rgb(248, 249, 251))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.rgb(220, 225, 232))
            }
        }

        val numero = TextView(this).apply {
            text = "PASSAGE ${i + 1}"
            textSize = 17f
            setTextColor(Color.rgb(10, 25, 50))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(12))
        }

        carte.addView(numero)

        val date = TextView(this).apply {
            text = "📅  Choisir une date"
            textSize = 16f
            setTextColor(Color.BLACK)
            setPadding(dp(14), dp(16), dp(14), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(2), Color.rgb(212, 175, 55))
            }

            setOnClickListener {
                val calendrier = java.util.Calendar.getInstance()

                android.app.DatePickerDialog(
                    this@MainActivity,
                    { _, annee, mois, jour ->

                        val nouvelleDate = String.format(
                            "%02d/%02d/%04d",
                            jour,
                            mois + 1,
                            annee
                        )

                        if (dates.withIndex().any {
                                it.index != i &&
                                it.value == nouvelleDate
                            }
                        ) {
                            Toast.makeText(
                                this@MainActivity,
                                "Cette date est déjà utilisée.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            dates[i] = nouvelleDate
                            text = "📅  $nouvelleDate"
                        }

                    },
                    calendrier.get(java.util.Calendar.YEAR),
                    calendrier.get(java.util.Calendar.MONTH),
                    calendrier.get(java.util.Calendar.DAY_OF_MONTH)
                ).apply {
                    datePicker.minDate =
                        System.currentTimeMillis() - 1000
                }.show()
            }
        }

        boutonsDates.add(date)
        carte.addView(date)

        val espace = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                1,
                dp(12)
            )
        }
        carte.addView(espace)

        val heure = TextView(this).apply {
            text = "🕒  Choisir une heure"
            textSize = 16f
            setTextColor(Color.BLACK)
            setPadding(dp(14), dp(16), dp(14), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(2), Color.rgb(212, 175, 55))
            }

            setOnClickListener {
                val maintenant = java.util.Calendar.getInstance()

                android.app.TimePickerDialog(
                    this@MainActivity,
                    { _, h, m ->

                        heures[i] = String.format(
                            "%02d:%02d",
                            h,
                            m
                        )

                        text = "🕒  ${heures[i]}"
                    },
                    maintenant.get(
                        java.util.Calendar.HOUR_OF_DAY
                    ),
                    maintenant.get(
                        java.util.Calendar.MINUTE
                    ),
                    true
                ).show()
            }
        }

        boutonsHeures.add(heure)
        carte.addView(heure)

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, dp(10), 0, dp(10))
        }

        layout.addView(carte, params)
    }

    val envoyer = bouton("ENVOYER MON CALENDRIER")

    envoyer.setOnClickListener {

        if (dates.any { it.isBlank() } ||
            heures.any { it.isBlank() }
        ) {
            Toast.makeText(
                this,
                "Veuillez compléter les quatre passages.",
                Toast.LENGTH_LONG
            ).show()

        } else if (dates.toSet().size != 4) {

            Toast.makeText(
                this,
                "Chaque passage doit avoir une date différente.",
                Toast.LENGTH_LONG
            ).show()

        } else {

            envoyer.isEnabled = false

            val passages = (0 until 4).map { i ->
                hashMapOf(
                    "date" to dates[i],
                    "heure" to heures[i]
                )
            }

            db.collection("clients")
                .document(utilisateur.uid)
                .collection("demandes")
                .document(demandeId)
                .update(
                    mapOf(
                        "joursPassage" to passages,
                        "statutCalendrier" to
                            "En attente de confirmation",
                        "dateEnvoiCalendrier" to
                            com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                )
                .addOnSuccessListener {

                    Toast.makeText(
                        this,
                        "Votre calendrier a été envoyé à Vibe Pro !",
                        Toast.LENGTH_LONG
                    ).show()

                    afficherEspaceClient(utilisateur)
                }
                .addOnFailureListener { erreur ->

                    envoyer.isEnabled = true

                    Toast.makeText(
                        this,
                        "Erreur : ${erreur.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    layout.addView(envoyer)

    val retour = bouton("RETOUR À MON ESPACE PERSONNEL")

    retour.setOnClickListener {
        afficherEspaceClient(utilisateur)
    }

    layout.addView(retour)

    setContentView(rendreDefilant(layout))
}



private fun afficherChoixVehiculeLavagePonctuel(
    utilisateur: FirebaseUser
) {
    val racine = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.WHITE)
    }

    val contenu = disposition()

    contenu.addView(titre("LAVAGE PONCTUEL"))

    contenu.addView(TextView(this).apply {
        text = "UN LAVAGE À LA HAUTEUR DE VOTRE VÉHICULE"
        textSize = 19f
        gravity = Gravity.CENTER
        setTypeface(null, android.graphics.Typeface.BOLD)
        setTextColor(Color.rgb(10, 25, 50))
        setPadding(dp(8), dp(12), dp(8), dp(8))
    })

    contenu.addView(TextView(this).apply {
        text = "Sélectionnez votre véhicule pour découvrir nos prestations et nos tarifs."
        textSize = 15f
        gravity = Gravity.CENTER
        setTextColor(Color.DKGRAY)
        setPadding(dp(12), dp(8), dp(12), dp(20))
    })

    val vehicules = listOf(
        "Berline" to "🚘",
        "SUV" to "🚙",
        "SUV double cabine" to "🛻",
        "Moto" to "🏍️",
        "Camion" to "🚚",
        "Mini-bus" to "🚐",
        "Bus" to "🚌"
    )

    for ((nom, emoji) in vehicules) {

        val carte = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))

            background = GradientDrawable().apply {
                setColor(Color.rgb(248, 249, 251))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.rgb(212, 175, 55))
            }

            isClickable = true
            isFocusable = true

            setOnClickListener {
                afficherFormulesLavagePonctuel(
                    utilisateur,
                    nom
                )
            }
        }

        val icone = TextView(this).apply {
            text = emoji
            textSize = 32f
            setPadding(0, 0, dp(16), 0)
        }

        val informations = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val nomVehicule = TextView(this).apply {
            text = nom
            textSize = 17f
            setTextColor(Color.rgb(10, 25, 50))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val description = TextView(this).apply {
            text = "Découvrir les tarifs et prestations"
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(5), 0, 0)
        }

        informations.addView(nomVehicule)
        informations.addView(description)

        carte.addView(icone)

        carte.addView(
            informations,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val fleche = TextView(this).apply {
            text = "›"
            textSize = 30f
            setTextColor(Color.rgb(212, 175, 55))
        }

        carte.addView(fleche)

        contenu.addView(
            carte,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(7), 0, dp(7))
            }
        )
    }

    val defilement = android.widget.ScrollView(this).apply {
        isFillViewport = true
        addView(contenu)
    }

    racine.addView(
        defilement,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
    )

    val retour = bouton("RETOUR AUX SERVICES")

    retour.setOnClickListener {
        afficherServices(utilisateur)
    }

    racine.addView(
        retour,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(dp(12), dp(8), dp(12), dp(12))
        }
    )

    setContentView(racine)
}




private fun afficherFormulesLavagePonctuel(
    utilisateur: FirebaseUser,
    vehicule: String
) {
    val racine = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }

    val contenu = disposition()

    contenu.addView(titre("FORMULES DE LAVAGE"))

    contenu.addView(TextView(this).apply {
        text = "Véhicule sélectionné : $vehicule"
        textSize = 17f
        gravity = Gravity.CENTER
        setPadding(12, 12, 12, 20)
    })

    val grille = creerGrille()

    val tarifs = when (vehicule) {
        "Berline" -> listOf(
            "Essentielle" to 1000,
            "Confort" to 1500,
            "Prestige" to 2000
        )

        "SUV" -> listOf(
            "Essentielle" to 1500,
            "Confort" to 2000,
            "Prestige" to 2500
        )

        "SUV double cabine" -> listOf(
            "Essentielle" to 2000,
            "Confort" to 2500,
            "Prestige" to 3000
        )

        "Moto" -> listOf(
            "Essentielle" to 500,
            "Confort" to 1000,
            "Prestige" to 1500
        )

        "Camion" -> listOf(
            "Essentielle" to 7000,
            "Confort" to 12000,
            "Prestige" to 30000
        )

        "Mini-bus" -> listOf(
            "Essentielle" to 3000,
            "Confort" to 5000,
            "Prestige" to 7000
        )

        "Bus" -> listOf(
            "Essentielle" to 15000,
            "Confort" to 20000,
            "Prestige" to 35000
        )

        else -> emptyList()
    }

    for ((formule, prix) in tarifs) {
        ajouterBoutonEnGrille(
            grille,
            "$formule\n$prix FCFA"
        ) {
            Toast.makeText(
                this,
                "$vehicule — $formule : $prix FCFA",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    contenu.addView(grille)

    val defilement = android.widget.ScrollView(this).apply {
        isFillViewport = true
        addView(contenu)
    }

    racine.addView(
        defilement,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
    )

    val retour = bouton("RETOUR AUX VÉHICULES")
    retour.setOnClickListener {
        afficherChoixVehiculeLavagePonctuel(utilisateur)
    }

    racine.addView(retour)

    setContentView(racine)
}


private fun creerGrille(): android.widget.GridLayout {
    return android.widget.GridLayout(this).apply {
        columnCount = 2
        rowCount = android.widget.GridLayout.UNDEFINED
        alignmentMode = android.widget.GridLayout.ALIGN_MARGINS
        useDefaultMargins = false

        setPadding(dp(4), dp(12), dp(4), dp(12))
        setBackgroundColor(Color.WHITE)
    }
}



private fun ajouterBoutonEnGrille(
    grille: android.widget.GridLayout,
    texte: String,
    action: () -> Unit
) {
    val icones = mapOf(
        "LAVAGE PONCTUEL" to "🚘",
        "Nettoyage industriel et immeubles" to "🏢",
        "Mécanique générale auto-moto" to "🔧",
        "Diagnostic automobile" to "🖥️",
        "Diagnostic visuel" to "🔍",
        "Entretien et réparation" to "⚙️",
        "Produits et accessoires auto" to "🛍️",
        "Dépannage léger" to "🚗",
        "Équipements de lavage" to "🧽",
        "Autres activités" to "✨"
    )

    val descriptions = mapOf(
        "LAVAGE PONCTUEL" to "Lavage professionnel",
        "Nettoyage industriel et immeubles" to "Nettoyage professionnel",
        "Mécanique générale auto-moto" to "Entretien et réparation",
        "Diagnostic automobile" to "Analyse électronique",
        "Diagnostic visuel" to "Contrôle du véhicule",
        "Entretien et réparation" to "Maintenance automobile",
        "Produits et accessoires auto" to "Boutique Vibe Pro",
        "Dépannage léger" to "Assistance automobile",
        "Équipements de lavage" to "Matériel professionnel",
        "Autres activités" to "Nos autres services"
    )

    val carte = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(18), dp(10), dp(18))

        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(18).toFloat()
            setStroke(dp(2), Color.rgb(212, 175, 55))
        }

        isClickable = true
        isFocusable = true

        setOnClickListener {
            action()
        }
    }

    val icone = TextView(this).apply {
        text = icones[texte] ?: "⭐"
        textSize = 32f
        gravity = Gravity.CENTER
    }

    val nom = TextView(this).apply {
        text = texte
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(10, 25, 50))
        setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(0, dp(10), 0, dp(5))
    }

    val description = TextView(this).apply {
        text = descriptions[texte] ?: "Découvrez notre service"
        textSize = 11f
        gravity = Gravity.CENTER
        setTextColor(Color.DKGRAY)
    }

    carte.addView(icone)
    carte.addView(nom)
    carte.addView(description)

    val parametres = android.widget.GridLayout.LayoutParams().apply {
        width = 0
        height = dp(145)

        columnSpec = android.widget.GridLayout.spec(
            android.widget.GridLayout.UNDEFINED,
            1f
        )

        setMargins(dp(6), dp(6), dp(6), dp(6))
    }

    grille.addView(carte, parametres)
}


private fun afficherMonCalendrierPassage(
    utilisateur: FirebaseUser
) {
    val racine = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.WHITE)
    }

    val layout = disposition()

    layout.addView(titre("MON CALENDRIER DE PASSAGE"))

    val introduction = TextView(this).apply {
        text = "Retrouvez ici vos quatre passages mensuels et leur état de confirmation."
        textSize = 15f
        gravity = Gravity.CENTER
        setTextColor(Color.DKGRAY)
        setPadding(dp(12), dp(12), dp(12), dp(20))
    }

    layout.addView(introduction)

    val chargement = TextView(this).apply {
        text = "Chargement de vos calendriers..."
        textSize = 15f
        gravity = Gravity.CENTER
        setTextColor(Color.DKGRAY)
        setPadding(dp(16), dp(25), dp(16), dp(25))
    }

    layout.addView(chargement)

    val defilement = android.widget.ScrollView(this).apply {
        isFillViewport = true
        addView(layout)
    }

    racine.addView(
        defilement,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
    )

    val retour = bouton("RETOUR À MON ESPACE")

    retour.setOnClickListener {
        afficherEspaceClient(utilisateur)
    }

    racine.addView(
        retour,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(dp(12), dp(8), dp(12), dp(12))
        }
    )

    setContentView(racine)

    db.collection("clients")
        .document(utilisateur.uid)
        .collection("demandes")
        .whereEqualTo("service", "Abonnement lavage")
        .get()
        .addOnSuccessListener { resultats ->

            layout.removeView(chargement)

         val demandes = resultats.documents.filter { document ->
    (document.get("joursPassage") as? List<*>)?.isNotEmpty() == true
}

            if (demandes.isEmpty()) {

                layout.addView(TextView(this).apply {
                    text = "Aucun calendrier enregistré pour le moment.\n\nDès que votre abonnement sera accepté et que vous aurez envoyé vos dates, votre calendrier apparaîtra ici."
                    textSize = 16f
                    gravity = Gravity.CENTER
                    setTextColor(Color.DKGRAY)
                    setPadding(dp(18), dp(30), dp(18), dp(30))
                    background = GradientDrawable().apply {
                        setColor(Color.rgb(245, 248, 252))
                        cornerRadius = dp(16).toFloat()
                    }
                })

                return@addOnSuccessListener
            }

            for (document in demandes) {

           val passages: List<*> =
    document.get("joursPassage") as? List<*> ?: emptyList<Any>()

                val statutCalendrier =
                    document.getString("statutCalendrier") ?: ""

                val confirme = statutCalendrier == "Confirmé"

                val carte = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(16), dp(16), dp(16))

                    background = GradientDrawable().apply {
                        setColor(Color.rgb(248, 249, 251))
                        cornerRadius = dp(18).toFloat()
                        setStroke(
                            dp(2),
                            Color.rgb(212, 175, 55)
                        )
                    }
                }

                carte.addView(TextView(this).apply {
                    text = "ABONNEMENT ${document.getString("formule") ?: ""}"
                    textSize = 18f
                    setTextColor(Color.rgb(10, 25, 50))
                    setTypeface(
                        null,
                        android.graphics.Typeface.BOLD
                    )
                    setPadding(0, 0, 0, dp(12))
                })

                carte.addView(TextView(this).apply {
                    text = "Véhicule : ${document.getString("typeVehicule") ?: "Non précisé"}"
                    textSize = 15f
                    setTextColor(Color.DKGRAY)
                })

                val statut = TextView(this).apply {
                    text = if (confirme) {
                        "✓ CALENDRIER CONFIRMÉ PAR VIBE PRO"
                    } else {
                        "◷ EN ATTENTE DE CONFIRMATION"
                    }

                    textSize = 13f
                    setTypeface(
                        null,
                        android.graphics.Typeface.BOLD
                    )

                    setTextColor(
                        if (confirme) {
                            Color.rgb(20, 125, 70)
                        } else {
                            Color.rgb(180, 120, 20)
                        }
                    )

                    setPadding(0, dp(14), 0, dp(14))
                }

                carte.addView(statut)

             if (passages.isEmpty()) {
                    carte.addView(TextView(this).apply {
                        text = "Aucun passage enregistré."
                        setTextColor(Color.DKGRAY)
                        textSize = 14f
                    })
                } else {

                    passages.forEachIndexed { index, element ->

                        val passage =
                            element as? Map<*, *> ?: emptyMap<Any, Any>()

                        val ligne = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(
                                dp(12),
                                dp(12),
                                dp(12),
                                dp(12)
                            )

                            background = GradientDrawable().apply {
                                setColor(Color.WHITE)
                                cornerRadius = dp(12).toFloat()
                                setStroke(
                                    dp(1),
                                    Color.rgb(225, 225, 225)
                                )
                            }
                        }

                        ligne.addView(TextView(this).apply {
                            text = "PASSAGE ${index + 1}"
                            textSize = 16f
                            setTextColor(Color.rgb(10, 25, 50))
                            setTypeface(
                                null,
                                android.graphics.Typeface.BOLD
                            )
                        })

                        ligne.addView(TextView(this).apply {
                            text = "📅 Date : ${passage["date"] ?: "Non renseignée"}"
                            textSize = 15f
                            setTextColor(Color.DKGRAY)
                            setPadding(0, dp(8), 0, dp(4))
                        })

                        ligne.addView(TextView(this).apply {
                            text = "🕒 Heure : ${passage["heure"] ?: "Non renseignée"}"
                            textSize = 15f
                            setTextColor(Color.DKGRAY)
                        })

                        carte.addView(ligne)

                        val espace = View(this).apply {
                            layoutParams =
                                LinearLayout.LayoutParams(
                                    1,
                                    dp(8)
                                )
                        }

                        carte.addView(espace)
                    }
                }

                if (confirme) {

                    carte.addView(TextView(this).apply {
                        text = "Votre calendrier est confirmé. Les modifications sont verrouillées."
                        textSize = 14f
                        setTextColor(Color.rgb(20, 125, 70))
                        setPadding(0, dp(14), 0, dp(5))
                    })

                } else {

                    val modifier = bouton("MODIFIER MON CALENDRIER")

                    modifier.setOnClickListener {

                        afficherFormulaireProgrammation(
                            utilisateur,
                            document.id,
                            document.getString("typeVehicule") ?: "",
                            document.getString("formule") ?: ""
                        )
                    }

                    carte.addView(modifier)
                }

                layout.addView(
                    carte,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(
                            0,
                            dp(10),
                            0,
                            dp(10)
                        )
                    }
                )
            }
        }
        .addOnFailureListener { erreur ->

            chargement.text =
                "Impossible de charger votre calendrier. ${erreur.message}"

            chargement.setTextColor(Color.RED)
        }
}
} 