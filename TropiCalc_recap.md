# TropiCalc — récap des mécaniques couvertes

Document de référence listant tout ce que le mod sait faire, à jour du commit `642f9ce`.
Vocabulaire : **estimé** = vient du set Smogon (affiché avec `?`) ; **confirmé** = déduit par observation en combat (fait certain, pas de `?`).

---

## 1. Calcul de dégâts

### Capacités à puissance variable
Elles ont toutes une puissance de base de 0 dans les données Showdown — sans traitement spécial, elles affichaient zéro dégât.

- Gyroball et Boule Élek : selon le ratio de vitesse (avec stages, Écharpe Choix, paralysie).
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
Lu depuis Cobblemon, avec les formes régionales exactes. Modifié par Heavy Metal, Light Metal, Pierre Allégée.

---

## 2. Projections résiduelles (cœur du stall)

Deux lignes séparées : **l'adversaire** (`Résiduel`) et **toi** (`Résiduel toi`).

Sources gérées : poison, Toxik (compteur croissant réel, suivi tour par tour), brûlure, tempête de sable (avec toutes les immunités de type et de talent), Restes, Boue Noire, Salaison (1/8, mais **1/4 pour les types Eau et Acier**), Vampigraine, Cuvette, Corps Gel.

Talents qui annulent/inversent : Garde Magik (aucun dégât indirect), Soin Poison (régénère au lieu de subir le poison).

Chaque projection affiche le KO estimé (« → KO ~N tours »). Ta ligne à toi utilise ton statut et ton objet réels, donc jamais de `?`.

### Pire cas du tour
`Pire cas : -X% (attaque adverse + résiduel) → survis à ~Y%` ou `→ KO possible !`. Combine la plus grosse attaque adverse estimée et ton propre résiduel. C'est un plancher fiable, pas une garantie absolue (une capacité hors-set peut le dépasser).

---

## 3. Détection par observation

Le mod déduit des faits en regardant les PV bouger, plutôt que de faire confiance à Smogon.

- **Restes** : soin de ~1/16 en fin de tour (gère aussi le soin plafonné près des PV max).
- **Casque Brut** : sur un tour propre (tu attaques au contact, l'adversaire ne t'attaque pas, tu es sain, pas de Vampigraine/Orbe Vie/sable), la perte de PV signe l'objet — 14-20 % = Casque seul, 25-33 % = Casque + Épine de Fer/Peau Dure.
- **Soin Poison** : un empoisonné qui gagne ~1/8 par tour (impossible autrement).
- **Objet retiré** : ton Sabotage réussi retire l'objet, qui n'est plus compté.

Un objet/talent confirmé est prioritaire sur l'estimation Smogon et fait tomber le `?`.

---

## 4. Recul par contact

Face à un porteur de Casque Brut et/ou Épine de Fer/Peau Dure, chaque attaque de contact affiche son coût : `Pisto-Poing : 15% - 18% | -29% toi`. Multiplié par le nombre de coups (Double Volée = ×2). S'applique à toutes tes attaques de contact ; les attaques sans contact n'affichent rien. La liste des capacités de contact est curatée (l'essentiel du compétitif, pas exhaustive).

---

## 5. Suivi de terrain et d'état

- **Compteur de PP adverses** : par espèce, persiste aux switchs dans le combat. Talent Pression pris en compte (seulement sur les capacités qui ciblent ton Pokémon).
- **Pièges d'entrée** (écran de switch) : Piège de Roc (efficacité Roche sur les types), Picots (couches, si au sol), Toile Gluante. Grosses Bottes, Garde Magik, Lévitation, Ballon reconnus. Affiche « entre à Y% PV » par candidat.
- **Verrou Choix** : objet Choix + un coup déjà utilisé depuis l'entrée = capacité verrouillée affichée.
- **Durées** : météo (~5 tours, hypothèse basse sans Roche Lisse) et écrans adverses, décomptés chaque tour.
- **Boosts** : suivis en direct, purgés entre combats et aux switchs (le fix des « stages fantômes »).

---

## 6. Scouting persistant entre combats

Les faits confirmés (objets, talents, Épine de Fer/Casque Brut, capacités révélées) sont sauvegardés dans `config/tropicalc-scouting.json`, indexés par **pseudo adverse + espèce**. Au combat suivant contre le même joueur, son Pokémon arrive pré-étiqueté (capacités cochées, objet/talent en estimation prioritaire, mais avec `?` car le set a pu changer).

---

## Limites connues (à valider en jeu)

1. Les détections par seuils de PV (Casque Brut, Restes, Soin Poison) n'ont pas été testées ensemble sur beaucoup de combats — des faux positifs restent possibles.
2. Le `ScoutingStore` écrit bien sur disque mais sa relecture au combat suivant n'a pas encore été vérifiée en conditions réelles.
3. Les listes de capacités (contact, multi-coups, variables) sont curatées à la main : justes pour ce qui est couvert, incomplètes par nature. Une capacité absente n'affiche rien, jamais un faux avertissement.
4. Reconnexion en plein combat : les compteurs (Toxik, PP, boosts) repartent de zéro.
5. Le poids des formes régionales est exact, mais un talent modifiant le poids côté adverse n'est pas toujours connu.

---

## Méthode de test qui marche

Ce qui a le plus fait progresser l'outil : jouer, repérer un écart entre l'affiché et le réel, puis remonter **les chiffres précis + une capture**. C'est comme ça qu'on a trouvé les stages fantômes et le talent Épine de Fer écrasé par l'inférence.

---

## Note de sécurité

Le token GitHub utilisé pour pousser le code doit être **révoqué et régénéré** entre les sessions (il a transité par la conversation). Créer un token *fine-grained* limité au seul dépôt `tropicalc`, permission Contents lecture/écriture, expiration courte.
