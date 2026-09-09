# TropiCalc — récap des mécaniques couvertes

Document de référence listant tout ce que le mod sait faire, à jour du commit `c0a919a`.
Vocabulaire : **estimé** = vient du set Smogon (affiché avec `?`) ; **confirmé** = déduit par observation en combat (fait certain, pas de `?`).

**Avant toute nouvelle session** : lire ce fichier en entier, vérifier `git log` (travail parallèle possible, plusieurs sessions/utilisateur peuvent committer directement), et la section "Méthode qui marche" avant de coder quoi que ce soit.

---

## 1. Calcul de dégâts

### Capacités à puissance variable
Puissance de base 0 dans les données Showdown, gérées à la main dans `puissanceEffective` (DamageCalculator) :
Gyroball/Boule Élek (ratio vitesse), Châtiment (×2 si statut), Façade (×2 si brûlure/poison/paralysie), Balayage/Nœud Herbe (poids cible), Tacle Lourd/Tacle Feu (ratio poids), Retour/Frustration/Puissance Cachée, Acrobatie (×2 sans objet), Force Ajoutée/Total Contrôle, Fléau/Contre, Éruption/Giclédo/Draco-Énergie, Ball'Météo, Triple Pied/Triple Axel, **Sabotage** (×1,5 si le défenseur a un objet — le talent **Glu** empêche le RETRAIT mais pas ce boost), **Poing de Colère** (+50 par capacité offensive réellement subie, max 6/plafond 350, compteur persistant PAR ESPÈCE qui ne reset JAMAIS au switch/K.O. — voir §3), **Prise de Bec/Branchicrok** (double à 170 si l'attaquant agit avant sa cible — comparaison de vitesse réelle en cas général, mais affichées en DEUX lignes "avt"/"après" dans les capacités adverses plutôt que de deviner l'ordre).

### Capacités spéciales
Choc Psy/Frappe Psy/Lame Ointe (Défense physique), Tricherie (stats/boosts du défenseur), Mille Flèches (perce Vol/Lévitation), Lyophilisation (toujours super efficace vs Eau), Œil Révélateur (Normal/Combat neutres vs Spectre).

### STAB proactif Protéen/Libéro (Miascarade/Pyrobut/Amphinobi)
Ces 3 espèces sont les SEULES du jeu où ce talent caché est quasi-systématique en compétitif. STAB affiché sur TOUS leurs coups dès l'entrée sur le terrain, **tant qu'aucun changement de type réel n'a été observé** (`!attaquant.isTypesModifies()`). Depuis la Gen 9, Protéen/Libéro ne se déclenche qu'**une seule fois par entrée** (pas à chaque coup comme en Gen 6-8) : dès qu'un type réel est confirmé, le STAB proactif s'arrête et seul ce type détermine le STAB normalement — se réactive automatiquement au prochain switch (TypeTracker remet `isTypesModifies()` à false).

### Ordre des multiplicateurs
Météo → critique → aléatoire (85-100%) → STAB → efficacité de type → étape "autre" (écrans + terrain défensif + objets/talents, combinés et arrondis une seule fois).

### Talents à portée globale
Épée/Tablettes/Urne/Perles du Fléau (-25% Défense/Attaque/Atq.Spé/Déf.Spé de tous sauf le porteur), Aura Sombre/Fée (+33%, inversé -25% par Aura Brisée).

### Talents qui contournent les immunités
Brise Moule/Turboblaze/Téravolt ignorent le talent défensif de la cible — sauf **Garde-Talent**, qui protège explicitement contre ce contournement.

---

## 2. Projections résiduelles (cœur du stall)

**N'affiche plus rien "au cas où"** : le talent/objet utilisé dans le calcul résiduel doit être CONFIRMÉ par observation, jamais une simple estimation Smogon (fix appliqué suite à un signalement utilisateur). Cas Soin Poison particulièrement traité : si ce talent fait partie des talents *réellement possibles* de l'espèce (vérifié via le Pokédex Cobblemon) et n'est pas confirmé, la composante poison/toxik est **totalement omise** plutôt que de deviner un sens qui pourrait être inversé.

Sources gérées : poison, Toxik, brûlure, sable, Restes, Boue Noire, Salaison, Vampigraine, Cuvette, Corps Gel, Peau Sèche. Talents qui annulent/inversent : Garde Magik, Soin Poison, Sel Purificateur (immunité totale, cohérent par construction).

---

## 3. Détection par observation (objets/talents adverses)

Trois familles de mécanismes qui alimentent les **mêmes structures centrales** :

### Heuristiques par seuils de PV (historiques)
Restes (~1/16 fin de tour), Casque Brut (tour propre, 14-33% de perte selon combinaison avec Épine de Fer/Peau Dure), Soin Poison (~1/8 gagné/tour).

### Confirmation directe par message explicite (plus fiable)
`ability.generic` (n'importe quel talent dès activation), `damage.rockyhelmet/ironbarbs/roughskin` (recul dès le premier coup), `heal.XXX` hors `.generic` (n'importe quel objet de soin dès le premier soin).

### Confirmation par comportement/narrowing (ajoutées cette session)
- **Bandeau/Lunettes/Mouchoir Choix, Orbe Vie** : le narrowing (SetInferenceEngine) éliminait déjà ces candidats via les dégâts observés et les appliquait *silencieusement* au calcul depuis plusieurs nuits — maintenant aussi confirmé visuellement (`OBJETS_CONFIRMES`) une fois qu'un seul candidat survit après 3 observations.
- **Mouchoir Choix** spécifiquement : confirmé aussi par preuve directe si la vitesse minimale observée dépasse ce que l'espèce pourrait atteindre au maximum sans lui (252 EV, meilleure nature, meilleur talent de vitesse plausible sous la météo actuelle, Pied Véloce inclus si un statut est actif).
- **Évoluroc** : nature différente (quasi-certitude statistique, pas comportementale) — confirmé si Smogon montre Évoluroc comme objet n°1 avec ≥50% d'usage (seuil abaissé spécifiquement pour cet objet : il n'a AUCUN effet sur un Pokémon totalement évolué, donc être n°1 est déjà auto-validant même sans dominance écrasante — couvre Porygon2 et les murs NFE moins extrêmes).

**Verrouillage du narrowing une fois confirmé** : dès qu'un objet/talent est confirmé (par n'importe lequel des mécanismes ci-dessus), `ProfilAdversaire.verrouillerSiConfirme()` fige ce candidat unique dans les 4 StatHypothesis (attaque/attaqueSpe/defense/defenseSpe) — le narrowing ne re-teste plus d'alternatives déjà écartées avec certitude, ce qui resserre les plages EV plus finement.

Ces confirmations priment sur les heuristiques et sur le scouting d'un combat précédent.

---

## 4. Set estimé — sélection dynamique parmi le top 3 Smogon

Au lieu d'afficher systématiquement le spread #1, le mod teste les 3 spreads Smogon les plus populaires contre les plages EV déjà narrowées et l'ordre d'action observé (`spreadPlusProbable`), et affiche le premier qui reste cohérent :
- Un spread est éliminé si un de ses 4 EV offensifs/défensifs sort de la plage narrowée, si sa nature est incompatible avec les flags boostée/neutre/baissée déjà déduits, **ou** si la vitesse qu'il impliquerait est inférieure à la vitesse minimale garantie par l'ordre d'action (cross-check ajouté en réutilisant `VITESSES_MIN_OBSERVEES`, déjà existant).
- Repli sur le spread #1 si aucune observation, ou si aucun des 3 ne reste cohérent.
- Indicateur visuel : la ligne change de couleur (teinte des faits confirmés) quand le spread affiché diffère du #1 par défaut.

**Plages EV resserrées à la source** : `calculerPlagesEV` ignore désormais les spreads dont le poids est inférieur à 15% du spread le plus populaire — un set marginal à 2-3% d'usage n'élargit plus la plage jusqu'à 0 EV. Les candidats objets pour le narrowing DÉFENSIF utilisent aussi les objets top Smogon (intersectés avec la liste défensive), plus seulement la liste générique — asymétrie avec le côté offensif corrigée.

---

## 5. Recul par contact
Casque Brut/Épine de Fer/Peau Dure : coût affiché par attaque de contact, multiplié par le nombre de coups.

---

## 6. Suivi de terrain et d'état

- **PP adverses** : Pression prise en compte (uniquement sur les capacités qui ciblent réellement le porteur, via le champ target Showdown).
- **Pièges d'entrée**, **Verrou Choix**, **Boosts** (reset au switch, indirect + message explicite).
- **Murs** (Protection/Mur Lumière/Voile Aurore) : affichés **des deux côtés** (adv ET toi, avec compteur de tours séparé — le côté joueur n'a pas besoin de correction Lumargile puisque son objet réel est toujours connu). Icône dédiée à gauche du texte (voir §9).
- **Inférence Lumargile par comportement** : si un mur dure plus longtemps que l'hypothèse de départ (5 tours sans Lumargile détecté) ne le permettrait, le mod comprend automatiquement que le porteur a Lumargile, corrige la durée restante (8 tours totaux) et confirme l'objet pour l'espèce qui l'a posé (capturée au moment du sidestart via le Pokémon adverse actif à cet instant).
- **Clone (Substitute)** : `start.substitute`/`end.substitute` (arg = le porteur directement), `fail.substitute` ignoré. Affiché par camp, icône dédiée.
- **Prescience (Future Sight)** : `start.futuresight` (lanceur) démarre un compteur de 2 tours confirmé par log réel ; `end.futuresight` (cible réelle à l'impact) reset immédiatement. **La cible peut différer de celle visée au lancement** si l'adversaire switch entre-temps (observé 2 fois sur 4 dans le log de référence) — l'indicateur ne nomme donc jamais de cible précise, juste le compte à rebours.
- **Type override (Protéen/Libéro/Détrempage)** : fiable depuis la découverte du bug de format (voir §10).
- **Métamorph/Imposteur** : capacités/stats de la cible copiée, PV/statut restent ceux de Métamorph.

---

## 7. Scouting persistant entre combats
`config/tropicalc-scouting.json`, indexé par pseudo adverse + espèce. Migration automatique des anciens noms français au chargement. **Jamais plus de 4 capacités cochées** pour une même espèce (fix majeur — voir §10) : une observation réelle de ce combat prime toujours sur une entrée de scouting ancien potentiellement obsolète.

---

## 8. Panneaux d'équipe PvP
Portage TropiHunterBoard 1.3.8 (MIT, PiikaPops). Lit les mêmes structures centrales que §3 — profite automatiquement de toute nouvelle confirmation. Limite cosmétique connue : la ligne "types" ne reflète pas la Téracristallisation.

---

## 9. Interface : touche F6, combat sauvage, icônes visuelles

- **Combat sauvage** : détection fiable confirmée par log réel (un Pokémon sauvage apparaît toujours "nu" dans les messages, jamais enveloppé dans `owned_pokemon`, et n'a jamais de switch/withdraw) — désactive le HUD principal et l'écran de switch automatiquement.
- **Touche F6** : bascule manuelle du mod entier (HUD + switch + panneau PvP), pour les modes non détectables automatiquement (Random Battle : pas de signal distinctif trouvé côté client, la randomisation se fait côté serveur avant le combat).
- **Icônes visuelles** : mur (pattern de briques) et Clone (silhouette fantomatique à deux yeux) — vraies textures PNG 16x16 générées avec Pillow (jamais téléchargées d'internet, pour éviter tout risque de droit d'auteur sur des assets tiers), affichées via `DrawContext.drawTexture` (signature 1.21.1 confirmée : sans le paramètre `renderLayers` ajouté en 1.21.2+), stockées dans `assets/tropicalc/textures/gui/`.

---

## 10. Bugs majeurs de cette session (par ordre de gravité)

1. **Chaque message de combat traité deux fois.** Deux Mixins différents (`BattleMessageHandlerMixin` sur `BattleMessageHandler.handle()`, `BattleMessagePacketMixin` sur le décodage réseau) appelaient tous les deux `MoveUseTracker.traiterMessage()` pour le même paquet. Impact réel limité au comptage de PP (seul point non-idempotent) — tout le reste (`Set.add`, `Map.put`) était protégé par construction. `BattleMessageHandlerMixin` supprimé (contenait aussi des logs de diagnostic résiduels jamais nettoyés).
2. **Le format des messages Cobblemon n'est pas uniforme.** Le changement de type (Protéen/Libéro) ne marchait jamais : le message envoie le type en string brute (`"Ice"`), pas en objet de traduction imbriqué comme `owned_pokemon`. Ne jamais supposer un format sans log réel.
3. **Désynchronisation des listes candidates d'inférence.** 26 talents/objets implémentés étaient absents de `SetInferenceEngine`, empêchant le narrowing de jamais converger dessus. Garde-fou ajouté (`InferenceCoverageCheck`, log au démarrage, ne couvre pas les cas testés "en dur").
4. **Plus de 4 capacités cochées possibles.** Le scouting de combats passés se fusionnait avec les observations du combat en cours sans jamais vérifier le total combiné (le set adverse peut changer entre deux combats). `ajouterCapaciteAdversaire()` centralisé, garantit structurellement le plafond de 4.
5. **Écharpe Choix n'existe pas** (Mouchoir Choix), **Argile Pouvoir n'existe pas** (Lumargile) — deux noms faux qui traînaient depuis plusieurs nuits sans jamais avoir été vérifiés.

Sets Smogon absents (espèces bannies/hors Pokédex régional) : National Dex Ubers ajouté comme 3e source de données (après National Dex et OU), couvre automatiquement la quasi-totalité des légendaires/fabuleux/bannis sans sets manuels à écrire un par un. Sets manuels (Dracovish, Dragapult) gardés comme filet de sécurité final en cas d'échec réseau.

---

## Limites connues (à valider en jeu)

1. Moteur de correction rapide désactivé (stable mais moins réactif à un set hors standard).
2. Reconnexion en plein combat : compteurs repartent de zéro.
3. Imposteur n'hérite pas des stages de boost de la cible au moment de la transformation.
4. Baies de résistance/stat et effets de stage à usage unique (Weakness/Blunder Policy) non modélisés — nécessitent un vrai suivi de consommation, absent de l'architecture. Testé et délibérément écarté pour Baie Sitrus (risque de sous-estimation silencieuse après consommation, pire que l'absence).
5. Garde-fou de couverture d'inférence : ne détecte pas les cas testés en dur (Robuste, Fantômasque, Ceinture Focus, Garde-Talent).
6. Tooltip PvP : ligne "types" ne reflète pas la Téracristallisation (cosmétique).
7. Poing de Colère + Clone au même tour : si le Clone se brise exactement le même tour qu'un coup, le compteur peut être sous-estimé d'un coup (jamais surestimé) — l'état "Clone actif" reflète déjà l'après-coup au moment du traitement.
8. Blabla Dodo (Sleep Talk) et Random Battle : pas de log réel disponible, formats de message/détection non implémentés plutôt que devinés.

---

## Méthode de test qui marche
**Jouer, repérer un écart entre l'affiché et le réel, remonter les chiffres précis + un extrait de `tropicalc-messages-debug.txt` (MessageDebugLogger, toujours actif, tronqué à 500 Ko) si possible.** Chaque bug majeur de cette session a été trouvé ainsi, jamais en devinant. Vérifier aussi les noms français sur au moins 2 sources concordantes (Poképédia en priorité) avant tout ajout — plusieurs noms plausibles mais faux ont été trouvés cette session (Écharpe Choix, Argile Pouvoir, Épée/Vase/Perles/Tablettes de Ruine).

---

## Note sur le travail en parallèle
Plusieurs conversations/l'utilisateur lui-même ont travaillé directement sur ce dépôt (au moins un commit manuel confirmé : fix d'ordre d'initialisation Java sur `CAPACITES_BALLE`/`CAPACITES_SON`, un bug de crash que la compilation seule ne pouvait pas révéler). Toujours vérifier `git log` en début de session.

## Note de sécurité
Token GitHub à révoquer et régénérer entre les sessions (fine-grained, dépôt `tropicalc` seul, Contents lecture/écriture). Un run de build anormalement lent s'est résolu par annulation + commit vide — pas nécessairement lié au code.
