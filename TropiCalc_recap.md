# TropiCalc — récap des mécaniques couvertes

Document de référence listant tout ce que le mod sait faire, à jour du commit `6cc24e8`.
Vocabulaire : **estimé** = vient du set Smogon (affiché avec `?`) ; **confirmé** = déduit par observation en combat (fait certain, pas de `?`).

---

## 1. Calcul de dégâts

### Capacités à puissance variable
Elles ont toutes une puissance de base de 0 dans les données Showdown — sans traitement spécial, elles affichaient zéro dégât.

- Gyroball et Boule Élek : selon le ratio de vitesse (avec stages, Écharpe Choix, paralysie, talents météo).
- Châtiment : ×2 si la cible a un statut.
- Façade : ×2 si brûlure/poison/paralysie, et ignore correctement la pénalité d'attaque de la brûlure.
- Balayage, Nœud Herbe : paliers selon le poids de la cible.
- Tacle Lourd, Tacle Feu : paliers selon le ratio de poids attaquant/défenseur.
- Retour et Frustration (102, bonheur supposé optimal), Puissance Cachée (60).
- Acrobatie (×2 sans objet), Force Ajoutée et Total Contrôle (20 + 20 par boost positif).
- Fléau et Contre (paliers de PV jusqu'à 200), Éruption/Giclédo/Draco-Énergie (proportionnel aux PV).
- Ball'Météo (type et puissance selon la météo, STAB recalculé).
- Triple Pied (60 total) et Triple Axel (120 total).

### Capacités spéciales
- Choc Psy, Frappe Psy, Lame Ointe : frappent la Défense physique (contournent aussi le boost Déf. Spé. Roche sous tempête de sable).
- Tricherie : utilise l'Attaque et les boosts du défenseur ; objet/talent du défenseur ignorés, brûlure de l'attaquant appliquée.
- Sabotage : ×1,5 si la cible tient un objet.

### Dégâts fixes
Frappe Atlas et Ombre Nocturne (= niveau, immunités respectées), Sonicboom, Draco-Rage, Requiem Final (PV actuels).

### Multi-coups
Total affiché = dégâts par coup × nombre de coups. Multi-Coups (Skill Link) force le maximum ; Dé Pipé donne 4-5 coups sur les capacités 2-5.

### Poids
Lu depuis Cobblemon, avec les formes régionales exactes. Modifié par Heavy Metal, Light Metal, Pierrallégée.

### Terrains
Champ Élec/Herbu/Psy : +30% sur le type correspondant si l'attaquant est au sol. Champ Brumeux : Dragon ×0,5 si le défenseur est au sol. Champ Herbu : Séisme/Ébranlement/Amplitude ×0,5 si le défenseur est au sol (en plus du boost Plante).

### Vitesse en combat
Stages, Écharpe Choix (×1,5), paralysie (×0,5), et les 4 talents météo qui doublent la vitesse : Chlorophylle (soleil), Glissade (pluie), Baigne Sable (sable), Chasse-Neige (neige). Même calcul utilisé partout (HUD principal, écran de switch, Gyroball/Boule Élek). La ligne Vitesse affiche aussi, en bleu entre parenthèses, la vitesse qu'aurait l'adversaire s'il tenait un Mouchoir Choix — masqué s'il est déjà connu comme porteur d'un objet Choix.

### Ordre des multiplicateurs
Conforme au jeu réel : météo → critique → aléatoire (85-100%) → STAB → efficacité de type → étape "autre" (écrans + terrain défensif + objets/talents comme Ceinture Pro/Filtre/Orbe Vie, combinés et arrondis **une seule fois**, pas trois fois séparément).

---

## 2. Projections résiduelles (cœur du stall)

Deux lignes séparées : **l'adversaire** (`Résiduel`) et **toi** (`Résiduel toi`).

Sources gérées : poison, Toxik (compteur croissant réel, suivi tour par tour), brûlure, tempête de sable (avec toutes les immunités de type et de talent), Restes, Boue Noire, Salaison (1/8, mais **1/4 pour les types Eau et Acier**), Vampigraine, Cuvette, Corps Gel, Peau Sèche (soigne 1/8 sous la pluie, blesse 1/8 sous le soleil).

Talents qui annulent/inversent : Garde Magik (aucun dégât indirect), Soin Poison (régénère au lieu de subir le poison).

Chaque projection affiche le KO estimé (« → KO ~N tours »). Ta ligne à toi utilise ton statut et ton objet réels, donc jamais de `?`.

---

## 3. Détection par observation

Le mod déduit des faits en regardant les PV bouger, plutôt que de faire confiance à Smogon.

- **Restes** : soin de ~1/16 en fin de tour (gère aussi le soin plafonné près des PV max).
- **Casque Brut** : sur un tour propre (tu attaques au contact, l'adversaire ne t'attaque pas, tu es sain, pas de Vampigraine/Orbe Vie/sable), la perte de PV signe l'objet — 14-20 % = Casque seul, 25-33 % = Casque + Épine de Fer/Peau Dure.
- **Soin Poison** : un empoisonné qui gagne ~1/8 par tour (impossible autrement).
- **Objet retiré** : ton Sabotage réussi retire l'objet, qui n'est plus compté.

Un objet/talent confirmé est prioritaire sur l'estimation Smogon et fait tomber le `?`.

Un moteur de correction automatique (facteurs mesurés sur l'écart prévu/réel) a été construit puis **désactivé** après avoir produit des dégâts affichés incorrects (double comptage des boosts, contamination par les résiduels). Le calcul repose actuellement sur le set Smogon estimé + l'inférence classique par hypothèses d'EV (seuil : 3 observations concordantes). Les facteurs continuent d'être mesurés en arrière-plan mais ne sont plus appliqués.

---

## 4. Recul par contact

Face à un porteur de Casque Brut et/ou Épine de Fer/Peau Dure, chaque attaque de contact affiche son coût : `Pisto-Poing : 15% - 18% | -29% toi`. Multiplié par le nombre de coups (Double Volée = ×2). S'applique à toutes tes attaques de contact ; les attaques sans contact n'affichent rien. La liste des capacités de contact est curatée (l'essentiel du compétitif, pas exhaustive).

---

## 5. Suivi de terrain et d'état

- **Compteur de PP adverses** : par espèce, persiste aux switchs dans le combat. Talent Pression pris en compte (seulement sur les capacités qui ciblent ton Pokémon).
- **Pièges d'entrée** (écran de switch) : Piège de Roc (efficacité Roche sur les types), Picots (couches, si au sol), Pics Toxik (Poison/Toxik selon les couches, immunité Poison/Acier), Toile Gluante. Grosses Bottes, Garde Magik, Lévitation, Ballon reconnus. Affiche « entre à Y% PV » par candidat.
- **Verrou Choix** : objet Choix + un coup déjà utilisé depuis l'entrée = capacité verrouillée affichée.
- **Durées** : météo (~5 tours, hypothèse basse sans Roche Lisse) et écrans adverses, décomptés chaque tour.
- **Boosts** : suivis en direct, purgés entre combats et aux switchs (le fix des « stages fantômes »). L'identité utilisée pour détecter un switch est toujours celle du Pokémon réel, jamais celle copiée sous Imposteur.
- **Métamorph/Imposteur** : capacités et stats affichées sont celles de la cible copiée ; PV max, PV actuels et statut restent ceux de Métamorph (override dédié).

---

## 6. Scouting persistant entre combats

Les faits confirmés (objets, talents, Épine de Fer/Casque Brut, capacités révélées) sont sauvegardés dans `config/tropicalc-scouting.json`, indexés par **pseudo adverse + espèce**. Au combat suivant contre le même joueur, son Pokémon arrive pré-étiqueté (capacités cochées, objet/talent en estimation prioritaire, mais avec `?` car le set a pu changer). Persistance vérifiée câblée correctement : `tick()` déclenche la sauvegarde dès que le combat se termine.

---

## 7. Panneaux d'équipe PvP

Portage fidèle du code 1.3.8 de TropiHunterBoard (licence MIT, attribution PiikaPops), avec enrichissements TropiCalc ajoutés strictement en surcouche d'affichage (aucune modification de la détection/scan d'origine) :

- Deux panneaux déplaçables (ton équipe / équipe adverse), sprites 3×2, PV, statuts, objets tenus, KO grisés.
- Positions persistées dans `config/tropicalc-panels.properties`.
- Tooltip adverse enrichi : objet/talent confirmés (✓) ou scoutés (?), capacités révélées avec PP restants colorés.
- Contour de cellule sur les adversaires sous statut résiduel (poison/Toxik/brûlure).
- Limite connue : la ligne "types" du tooltip ne reflète pas la Téracristallisation en cours (affiche les types de base). Cosmétique uniquement, sans impact sur le calcul de dégâts principal.

---

## Audit systématique du moteur (dernière grosse session)

Passage complet du moteur de calcul, zone par zone, après plusieurs signalements de chiffres suspects. Méthode : pour toute correspondance id-technique → nom-français, rechercher le nom officiel avant de coder plutôt que de deviner.

**Bugs trouvés et corrigés :**
- Dictionnaire de traduction (le plus rentable, 9 corrections) : Glissade/Chlorophylle/Baigne Sable/Chasse-Neige, Casque Brut (mappé à tort "Casque Clou"), Grosses Bottes (faute de frappe), Soin Poison, Multi-Coups, Heavy Metal/Light Metal/Pierrallégée, Agitation (mappé à tort "Adrénaline").
- Garde Mystik (Wonder Guard) ignorait le type Téracristal.
- Flooring en 3 étapes séparées au lieu d'1 seule (écrans+terrain+objets), grignotait quelques points de dégâts.
- Champ Brumeux totalement inerte ; Champ Herbu incomplet (Séisme/Ébranlement/Amplitude non réduits).
- `estAuSol` ignorait le Ballon.
- Identité instable dans `BoostTracker.verifierActifs` sous transformation (Imposteur).
- Peau Sèche : effet passif de fin de tour absent de la projection résiduelle.
- HUD principal en coordonnées 100% fixes, risque de sortie d'écran sur petite résolution — ajustement dynamique ajouté.

**Confirmé sain après vérification, sans modification :**
- STAB/Téracristallisation : les 4 cas (type original, Tera=type original, Tera nouveau+capacité Tera, Tera nouveau+capacité originale) conformes aux règles officielles.
- Gestion des critiques (ignore les stages défavorables des deux côtés) correcte et symétrique.
- Simulation résiduelle tour par tour : sommer les deltas d'un même tour est mathématiquement identique à leur application séquentielle (aucun effet modélisé n'a de seuil dépendant de l'ordre).
- Listes de showdown ids (capacités de contact, multi-coups, protection, soin/drain) : aucune traduction possible à ce niveau, donc structurellement à l'abri du bug de dictionnaire ; spot-check sans erreur trouvée.

**Gap identifié, volontairement non comblé :** les baies de résistance de type (Prine, Coba, Chilan...) ne sont pas modélisées. Les ajouter sans infrastructure de suivi de consommation à usage unique risquerait de sous-estimer indéfiniment les dégâts après leur activation (bug plus dangereux que l'absence actuelle).

---

## Limites connues (à valider en jeu)

1. Le moteur de correction rapide par observation est désactivé (voir section 3) — le calcul actuel est stable mais moins réactif à un set adverse qui diverge du standard Smogon.
2. Les détections par seuils de PV (Casque Brut, Restes, Soin Poison) n'ont pas été testées ensemble sur beaucoup de combats — des faux positifs restent possibles.
3. Les listes de capacités (contact, multi-coups, variables) sont curatées à la main : justes pour ce qui est couvert, incomplètes par nature. Une capacité absente n'affiche rien, jamais un faux avertissement.
4. Reconnexion en plein combat : les compteurs (Toxik, PP, boosts) repartent de zéro.
5. Imposteur ne modélise pas l'héritage des stages de boost de la cible au moment de la transformation (mécanique officielle non simulée).
6. Baies de résistance de type non modélisées (voir audit ci-dessus).

---

## Méthode de test qui marche

Ce qui a le plus fait progresser l'outil : jouer, repérer un écart entre l'affiché et le réel, puis remonter **les chiffres précis + une capture**. C'est comme ça qu'on a trouvé les stages fantômes, le talent Épine de Fer écrasé par l'inférence, et le déclenchement du grand audit du dictionnaire de traduction.

---

## Note sur le travail en parallèle

Il est arrivé que deux conversations travaillent simultanément sur ce dépôt. Les pushs se sont toujours fusionnés proprement (fast-forward), mais toujours vérifier `git log` en début de session pour repérer d'éventuels commits inattendus avant de continuer.

---

## Note de sécurité

Le token GitHub utilisé pour pousser le code doit être **révoqué et régénéré** entre les sessions (il a transité par la conversation). Créer un token *fine-grained* limité au seul dépôt `tropicalc`, permission Contents lecture/écriture (+ Workflows lecture/écriture pour conserver le diagnostic automatique des builds échoués via la branche `ci-log`), expiration courte.
