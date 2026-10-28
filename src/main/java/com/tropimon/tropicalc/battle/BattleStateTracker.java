package com.tropimon.tropicalc.battle;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattleActor;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.pokemon.status.PersistentStatus;
import com.tropimon.tropicalc.calc.Nature;
import com.tropimon.tropicalc.calc.Pokemon;
import com.tropimon.tropicalc.calc.PokemonType;
import com.tropimon.tropicalc.calc.ShowdownIdMapper;
import com.tropimon.tropicalc.calc.Stat;
import com.tropimon.tropicalc.calc.SmogonDataLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;

import java.util.List;
import java.util.UUID;

public final class BattleStateTracker {

    private BattleStateTracker() {
    }

    public static boolean estEnCombat() {
        return CobblemonClient.INSTANCE.getBattle() != null;
    }

    public static Pokemon getJoueurActifDepuisEquipe() {
        com.cobblemon.mod.common.pokemon.Pokemon complet = getPokemonCompletJoueur();
        if (complet == null) return null;
        return convertirPokemonComplet(complet);
    }

    /**
     * Mon Pokémon actif tel que le combat le voit (PV, statut, boosts du
     * combat). Les IV, EV et nature, absents des données de combat, viennent
     * de l'équipe : sans eux les PV max étaient trop bas (Roigada 300 au lieu
     * de 394) et chaque % de PV perdu était faux, d'où de faux « moins que
     * prévu » et des plafonds d'Attaque adverses appliqués à tort.
     */
    public static Pokemon getJoueurActif() {
        ClientBattleActor acteur = getActeurJoueur();
        if (acteur == null || acteur.getActivePokemon().isEmpty()) return null;
        ClientBattlePokemon cbp = acteur.getActivePokemon().get(0).getBattlePokemon();
        if (cbp == null) return null;
        com.cobblemon.mod.common.pokemon.Pokemon complet = null;
        try {
            for (com.cobblemon.mod.common.pokemon.Pokemon p : acteur.getPokemon()) {
                if (p.getUuid().equals(cbp.getUuid())) {
                    complet = p;
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        return convertir(cbp, complet);
    }

    /**
     * PV réels (absolus) de mon Pokémon actif lus dans le combat, -1 si
     * indisponibles. Sert pour un Métamorph transformé : l'équipe n'est plus
     * mise à jour pendant la transformation (log du 13/10 : Métamorph à 15 %
     * dans l'équipe alors que le Vœu l'avait remis à 100 %).
     */
    public static int getPvBrutJoueurActif() {
        try {
            ClientBattleActor acteur = getActeurJoueur();
            if (acteur == null || acteur.getActivePokemon().isEmpty()) return -1;
            ClientBattlePokemon cbp = acteur.getActivePokemon().get(0).getBattlePokemon();
            if (cbp == null || !cbp.isHpFlat()) return -1;
            return Math.round(cbp.getHpValue());
        } catch (Exception e) {
            return -1;
        }
    }

    /** UUID de mon Pokémon actif, ou null. */
    public static java.util.UUID getUuidJoueurActif() {
        try {
            ClientBattleActor acteur = getActeurJoueur();
            if (acteur == null || acteur.getActivePokemon().isEmpty()) return null;
            ClientBattlePokemon cbp = acteur.getActivePokemon().get(0).getBattlePokemon();
            return cbp == null ? null : cbp.getUuid();
        } catch (Exception e) {
            return null;
        }
    }

    public static Pokemon getAdversaireActif() {
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        ClientBattleActor acteurJoueur = getActeurJoueur();
        if (battle == null || acteurJoueur == null) return null;
        for (var side : battle.getSides()) {
            if (!side.getActors().contains(acteurJoueur)) {
                for (ClientBattleActor acteur : side.getActors()) {
                    Pokemon p = premierActif(acteur);
                    if (p != null) {
                        p.setCampAdverse(true);
                        return p;
                    }
                }
            }
        }
        return null;
    }

    /** Équipe complète de l'adversaire (pour Général Suprême et similaires). */
    public static List<com.cobblemon.mod.common.pokemon.Pokemon> getEquipeAdversaire() {
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        ClientBattleActor acteurJoueur = getActeurJoueur();
        if (battle == null || acteurJoueur == null) return null;
        for (var side : battle.getSides()) {
            if (!side.getActors().contains(acteurJoueur)) {
                for (ClientBattleActor acteur : side.getActors()) {
                    return acteur.getPokemon();
                }
            }
        }
        return null;
    }

    /**
     * Vrai si le Pokémon actif du joueur est transformé (Métamorph/Imposteur,
     * Morphing) : l'espèce en combat diffère de celle de l'entité d'équipe.
     */
    public static boolean joueurEstTransforme() {
        ClientBattleActor acteur = getActeurJoueur();
        if (acteur == null || acteur.getActivePokemon().isEmpty()) return false;
        ClientBattlePokemon actif = acteur.getActivePokemon().get(0).getBattlePokemon();
        if (actif == null) return false;
        try {
            String especeCombat = actif.getSpecies().getName();
            for (com.cobblemon.mod.common.pokemon.Pokemon p : acteur.getPokemon()) {
                if (p.getUuid().equals(actif.getUuid())) {
                    return !especeCombat.equalsIgnoreCase(p.getSpecies().getName());
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Capacités du menu d'attaque (requête d'action du jeu) de MON Pokémon
     * actif, en id Showdown. Pour un Métamorph transformé, ce sont les
     * capacités copiées. null si aucune requête pour ce Pokémon.
     */
    public static List<String> getCapacitesRequeteJoueur() {
        try {
            ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
            ClientBattleActor acteur = getActeurJoueur();
            if (battle == null || acteur == null || acteur.getActivePokemon().isEmpty()) return null;
            ClientBattlePokemon actif = acteur.getActivePokemon().get(0).getBattlePokemon();
            if (actif == null) return null;
            List<com.cobblemon.mod.common.client.battle.SingleActionRequest> requetes = battle.getPendingActionRequests();
            if (requetes == null) return null;
            for (com.cobblemon.mod.common.client.battle.SingleActionRequest r : requetes) {
                // La requête doit concerner ce Pokémon (pas celui du tour d'avant)
                ActiveClientBattlePokemon concerne = r.getActivePokemon();
                if (concerne == null || concerne.getBattlePokemon() == null
                        || !actif.getUuid().equals(concerne.getBattlePokemon().getUuid())) continue;
                com.cobblemon.mod.common.battles.ShowdownMoveset ms = r.getMoveSet();
                if (ms == null || ms.getMoves() == null) continue;
                List<String> ids = new java.util.ArrayList<>();
                for (com.cobblemon.mod.common.battles.InBattleMove m : ms.getMoves()) {
                    if (m != null && m.getId() != null) ids.add(m.getId().toLowerCase().replaceAll("[^a-z0-9]", ""));
                }
                if (!ids.isEmpty()) return ids;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Le Pokémon adverse dont les capacités sont copiées quand on est transformé. */
    public static com.cobblemon.mod.common.pokemon.Pokemon getSourceTransformation() {
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        ClientBattleActor acteurJoueur = getActeurJoueur();
        if (battle == null || acteurJoueur == null) return null;
        for (var side : battle.getSides()) {
            if (!side.getActors().contains(acteurJoueur)) {
                for (ClientBattleActor acteur : side.getActors()) {
                    if (acteur.getActivePokemon().isEmpty()) continue;
                    ClientBattlePokemon cbp = acteur.getActivePokemon().get(0).getBattlePokemon();
                    if (cbp == null) continue;
                    UUID uuid = cbp.getUuid();
                    for (com.cobblemon.mod.common.pokemon.Pokemon p : acteur.getPokemon()) {
                        if (p.getUuid().equals(uuid)) return p;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Le Pokémon actif du joueur pour l'affichage des capacités.
     * Si transformé (Imposteur), renvoie la source copiée afin d'afficher
     * les capacités réellement disponibles, et non celles de Métamorph.
     */
    public static com.cobblemon.mod.common.pokemon.Pokemon getPokemonCompletJoueurAffichage() {
        if (joueurEstTransforme()) {
            com.cobblemon.mod.common.pokemon.Pokemon source = getSourceTransformation();
            if (source != null) return source;
        }
        return getPokemonCompletJoueur();
    }

    public static com.cobblemon.mod.common.pokemon.Pokemon getPokemonCompletJoueur() {
        ClientBattleActor acteur = getActeurJoueur();
        if (acteur == null || acteur.getActivePokemon().isEmpty()) return null;
        ClientBattlePokemon actif = acteur.getActivePokemon().get(0).getBattlePokemon();
        if (actif == null) return null;
        UUID uuid = actif.getUuid();
        for (com.cobblemon.mod.common.pokemon.Pokemon p : acteur.getPokemon()) {
            if (p.getUuid().equals(uuid)) return p;
        }
        return null;
    }

    /** Renvoie la liste des Pokémon de l'équipe du joueur (pour l'aperçu de switch). */
    public static List<com.cobblemon.mod.common.pokemon.Pokemon> getEquipeJoueur() {
        ClientBattleActor acteur = getActeurJoueur();
        if (acteur == null) return null;
        return acteur.getPokemon();
    }

    /** Convertit un membre de l'équipe en calc.Pokemon (vraies stats). */
    public static Pokemon convertirMembre(com.cobblemon.mod.common.pokemon.Pokemon p) {
        return convertirPokemonComplet(p);
    }

    /** Combat en cours où je ne suis pas un des dresseurs (je le regarde). */
    public static boolean estSpectateur() {
        try {
            return CobblemonClient.INSTANCE.getBattle() != null && getActeurJoueur() == null;
        } catch (Exception e) {
            return false;
        }
    }

    /** Format du combat en cours (ou du dernier) : vrai random, faux classé, null inconnu. */
    private static Boolean formatRandom = null;
    private static Object combatFormatDetecte = null;

    /**
     * Random ou classé, d'après les niveaux de MON équipe : en random chaque
     * Pokémon a son niveau (78 à 98), en classé toute l'équipe est au même
     * niveau. Gardé après la fin du combat pour le bilan.
     */
    public static Boolean estFormatRandom() {
        return formatRandom;
    }

    /** Une fois par combat, dès que mon équipe est connue. */
    public static void detecterFormat() {
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        if (battle == null || battle == combatFormatDetecte) return;
        ClientBattleActor acteur = getActeurJoueur();
        if (acteur == null) return;
        try {
            java.util.Set<Integer> niveaux = new java.util.TreeSet<>();
            int n = 0;
            for (com.cobblemon.mod.common.pokemon.Pokemon p : acteur.getPokemon()) {
                niveaux.add(p.getLevel());
                n++;
            }
            if (n == 0) return;
            combatFormatDetecte = battle;
            // Seulement contre un autre joueur : sauvage ou dresseur PNJ
            // (niveaux variés aussi) ne doivent rien changer
            boolean contreJoueur = false;
            var reseau = MinecraftClient.getInstance().getNetworkHandler();
            for (var side : battle.getSides()) {
                for (ClientBattleActor autre : side.getActors()) {
                    if (autre == acteur || autre.getUuid() == null) continue;
                    if (reseau != null && reseau.getPlayerListEntry(autre.getUuid()) != null) contreJoueur = true;
                }
            }
            if (!contreJoueur) {
                formatRandom = null;
                MessageDebugLogger.analyse("FORMAT : pas contre un joueur, non déterminé");
                return;
            }
            if (niveaux.size() > 1) formatRandom = Boolean.TRUE;
            else formatRandom = n >= 3 ? Boolean.FALSE : null;
            MessageDebugLogger.analyse("FORMAT : " + (formatRandom == null ? "inconnu"
                : formatRandom ? "random" : "classé") + " (niveaux de mon équipe " + niveaux + ")");
        } catch (Exception ignored) {
        }
    }

    private static ClientBattleActor getActeurJoueur() {
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        if (battle == null) return null;
        var joueur = MinecraftClient.getInstance().player;
        if (joueur == null) return null;
        UUID uuid = joueur.getUuid();
        for (var side : battle.getSides()) {
            for (ClientBattleActor acteur : side.getActors()) {
                if (acteur.getUuid().equals(uuid)) return acteur;
            }
        }
        return null;
    }

    private static Pokemon premierActif(ClientBattleActor acteur) {
        if (acteur.getActivePokemon().isEmpty()) return null;
        ClientBattlePokemon cbp = acteur.getActivePokemon().get(0).getBattlePokemon();
        if (cbp == null) return null;
        return convertir(cbp);
    }

    private static Pokemon convertir(ClientBattlePokemon cbp) {
        return convertir(cbp, null);
    }

    /** complet : le Pokémon d'équipe correspondant (mes Pokémon), sinon null. */
    private static Pokemon convertir(ClientBattlePokemon cbp, com.cobblemon.mod.common.pokemon.Pokemon complet) {
        Species espece = cbp.getSpecies();
        PokemonProperties props = cbp.getProperties();

        var forme = espece.getForm(cbp.getState().getCurrentAspects());
        noterForme(props.getSpecies(), forme);
        PokemonType type1 = ShowdownIdMapper.type(forme.getPrimaryType().getName());
        PokemonType type2 = forme.getSecondaryType() != null
            ? ShowdownIdMapper.type(forme.getSecondaryType().getName()) : null;

        Pokemon.Builder builder = Pokemon.builder(props.getSpecies(), cbp.getLevel(), type1, type2)
            .statBase(Stat.PV, statBase(forme, Stats.HP))
            .statBase(Stat.ATTAQUE, statBase(forme, Stats.ATTACK))
            .statBase(Stat.DEFENSE, statBase(forme, Stats.DEFENCE))
            .statBase(Stat.ATTAQUE_SPE, statBase(forme, Stats.SPECIAL_ATTACK))
            .statBase(Stat.DEFENSE_SPE, statBase(forme, Stats.SPECIAL_DEFENCE))
            .statBase(Stat.VITESSE, statBase(forme, Stats.SPEED))
            .poids(forme.getWeight())
            .nature(props.getNature() != null ? ShowdownIdMapper.nature(props.getNature()) : Nature.HARDI);

        if (props.getAbility() != null) {
            String t = ShowdownIdMapper.talent(props.getAbility());
            if (t != null) builder.talent(t);
        }
        if (props.getHeldItem() != null) {
            String o = ShowdownIdMapper.objet(props.getHeldItem());
            if (o != null) builder.objet(o);
        }
        if (props.getTeraType() != null) {
            PokemonType tera = ShowdownIdMapper.type(props.getTeraType());
            if (tera != null) builder.teraType(tera);
        }
        if (props.getIvs() != null) {
            builder.iv(Stat.PV, props.getIvs().getOrDefault(Stats.HP));
            builder.iv(Stat.ATTAQUE, props.getIvs().getOrDefault(Stats.ATTACK));
            builder.iv(Stat.DEFENSE, props.getIvs().getOrDefault(Stats.DEFENCE));
            builder.iv(Stat.ATTAQUE_SPE, props.getIvs().getOrDefault(Stats.SPECIAL_ATTACK));
            builder.iv(Stat.DEFENSE_SPE, props.getIvs().getOrDefault(Stats.SPECIAL_DEFENCE));
            builder.iv(Stat.VITESSE, props.getIvs().getOrDefault(Stats.SPEED));
        }
        if (props.getEvs() != null) {
            builder.ev(Stat.PV, props.getEvs().getOrDefault(Stats.HP));
            builder.ev(Stat.ATTAQUE, props.getEvs().getOrDefault(Stats.ATTACK));
            builder.ev(Stat.DEFENSE, props.getEvs().getOrDefault(Stats.DEFENCE));
            builder.ev(Stat.ATTAQUE_SPE, props.getEvs().getOrDefault(Stats.SPECIAL_ATTACK));
            builder.ev(Stat.DEFENSE_SPE, props.getEvs().getOrDefault(Stats.SPECIAL_DEFENCE));
            builder.ev(Stat.VITESSE, props.getEvs().getOrDefault(Stats.SPEED));
        }

        // Mes Pokémon : IV, EV et nature de l'équipe (même espèce seulement ;
        // un Métamorph transformé garde la conversion du combat)
        if (complet != null && complet.getSpecies().getName().equalsIgnoreCase(espece.getName())) {
            try {
                for (var e : java.util.Map.of(Stat.PV, Stats.HP, Stat.ATTAQUE, Stats.ATTACK,
                        Stat.DEFENSE, Stats.DEFENCE, Stat.ATTAQUE_SPE, Stats.SPECIAL_ATTACK,
                        Stat.DEFENSE_SPE, Stats.SPECIAL_DEFENCE, Stat.VITESSE, Stats.SPEED).entrySet()) {
                    builder.iv(e.getKey(), complet.getIvs().getOrDefault(e.getValue()));
                    builder.ev(e.getKey(), complet.getEvs().getOrDefault(e.getValue()));
                }
                builder.nature(ShowdownIdMapper.nature(complet.getEffectiveNature().getName().getPath()));
            } catch (Exception ignored) {
            }
        }

        Pokemon pokemon = builder.build();

        if (cbp.isHpFlat()) {
            pokemon.setPvActuels(Math.round(cbp.getHpValue()));
        } else {
            pokemon.setPvActuels(Math.round(cbp.getHpValue() * pokemon.getPvMax()));
        }

        PersistentStatus statut = cbp.getStatus();
        if (statut != null) {
            switch (statut.getShowdownName()) {
                case "brn" -> pokemon.setStatut(Pokemon.Statut.BRULURE);
                case "par" -> pokemon.setStatut(Pokemon.Statut.PARALYSIE);
                case "psn" -> pokemon.setStatut(Pokemon.Statut.POISON);
                case "tox" -> pokemon.setStatut(Pokemon.Statut.POISON_GRAVE);
                case "slp" -> pokemon.setStatut(Pokemon.Statut.SOMMEIL);
                case "frz" -> pokemon.setStatut(Pokemon.Statut.GEL);
                default -> pokemon.setStatut(Pokemon.Statut.AUCUN);
            }
        }

        if (cbp.getStatChanges() != null) {
            Integer atk = cbp.getStatChanges().get(Stats.ATTACK);
            Integer def = cbp.getStatChanges().get(Stats.DEFENCE);
            Integer spa = cbp.getStatChanges().get(Stats.SPECIAL_ATTACK);
            Integer spd = cbp.getStatChanges().get(Stats.SPECIAL_DEFENCE);
            Integer spe = cbp.getStatChanges().get(Stats.SPEED);
            if (atk != null) pokemon.setStage(Stat.ATTAQUE, atk);
            if (def != null) pokemon.setStage(Stat.DEFENSE, def);
            if (spa != null) pokemon.setStage(Stat.ATTAQUE_SPE, spa);
            if (spd != null) pokemon.setStage(Stat.DEFENSE_SPE, spd);
            if (spe != null) pokemon.setStage(Stat.VITESSE, spe);
        }

        return pokemon;
    }

    private static Pokemon convertirPokemonComplet(com.cobblemon.mod.common.pokemon.Pokemon p) {
        Species espece = p.getSpecies();

        var forme = espece.getForm(p.getAspects());
        noterForme(espece.showdownId(), forme);
        PokemonType type1 = ShowdownIdMapper.type(forme.getPrimaryType().getName());
        PokemonType type2 = forme.getSecondaryType() != null
            ? ShowdownIdMapper.type(forme.getSecondaryType().getName()) : null;

        Pokemon.Builder builder = Pokemon.builder(espece.showdownId(), p.getLevel(), type1, type2)
            .statBase(Stat.PV, statBase(forme, Stats.HP))
            .statBase(Stat.ATTAQUE, statBase(forme, Stats.ATTACK))
            .statBase(Stat.DEFENSE, statBase(forme, Stats.DEFENCE))
            .statBase(Stat.ATTAQUE_SPE, statBase(forme, Stats.SPECIAL_ATTACK))
            .statBase(Stat.DEFENSE_SPE, statBase(forme, Stats.SPECIAL_DEFENCE))
            .statBase(Stat.VITESSE, statBase(forme, Stats.SPEED))
            .poids(forme.getWeight());

        builder.iv(Stat.PV, p.getIvs().getOrDefault(Stats.HP));
        builder.iv(Stat.ATTAQUE, p.getIvs().getOrDefault(Stats.ATTACK));
        builder.iv(Stat.DEFENSE, p.getIvs().getOrDefault(Stats.DEFENCE));
        builder.iv(Stat.ATTAQUE_SPE, p.getIvs().getOrDefault(Stats.SPECIAL_ATTACK));
        builder.iv(Stat.DEFENSE_SPE, p.getIvs().getOrDefault(Stats.SPECIAL_DEFENCE));
        builder.iv(Stat.VITESSE, p.getIvs().getOrDefault(Stats.SPEED));

        builder.ev(Stat.PV, p.getEvs().getOrDefault(Stats.HP));
        builder.ev(Stat.ATTAQUE, p.getEvs().getOrDefault(Stats.ATTACK));
        builder.ev(Stat.DEFENSE, p.getEvs().getOrDefault(Stats.DEFENCE));
        builder.ev(Stat.ATTAQUE_SPE, p.getEvs().getOrDefault(Stats.SPECIAL_ATTACK));
        builder.ev(Stat.DEFENSE_SPE, p.getEvs().getOrDefault(Stats.SPECIAL_DEFENCE));
        builder.ev(Stat.VITESSE, p.getEvs().getOrDefault(Stats.SPEED));

        builder.nature(ShowdownIdMapper.nature(p.getEffectiveNature().getName().getPath()));

        String talentFr = ShowdownIdMapper.talent(p.getAbility().getName());
        if (talentFr != null) builder.talent(talentFr);

        if (!p.heldItem().isEmpty()) {
            var itemId = Registries.ITEM.getId(p.heldItem().getItem());
            if (itemId != null) {
                String objetFr = ShowdownIdMapper.objet(itemId.getPath());
                if (objetFr != null) builder.objet(objetFr);
            }
        }

        Pokemon pokemon = builder.build();
        // getCurrentHealth() n'est pas tenu à jour en combat (Bétochef à
        // 56 % au lieu de 317/317) : PV du serveur / du terrain d'abord
        int pvReels = PvEquipe.pv(p.getUuid());
        pokemon.setPvActuels(pvReels >= 0 ? pvReels : p.getCurrentHealth());

        if (p.getStatus() != null) {
            switch (p.getStatus().getStatus().getShowdownName()) {
                case "brn" -> pokemon.setStatut(Pokemon.Statut.BRULURE);
                case "par" -> pokemon.setStatut(Pokemon.Statut.PARALYSIE);
                case "psn" -> pokemon.setStatut(Pokemon.Statut.POISON);
                case "tox" -> pokemon.setStatut(Pokemon.Statut.POISON_GRAVE);
                case "slp" -> pokemon.setStatut(Pokemon.Statut.SOMMEIL);
                case "frz" -> pokemon.setStatut(Pokemon.Statut.GEL);
            }
        }

        return pokemon;
    }

    // ------------------------------------------------------------------
    // Combat double
    // ------------------------------------------------------------------

    /** Un Pokémon sur le terrain en double : calcul, uuid, clé des messages (camp + nom affiché). */
    public record ActifDouble(Pokemon pokemon, com.cobblemon.mod.common.pokemon.Pokemon complet,
                              UUID uuid, String cle) {
    }

    /** Clé d'un Pokémon dans les messages : "J:" (moi) ou "A:" (adversaire) + nom affiché en minuscules. */
    public static String cleDouble(boolean adverse, String nomAffiche) {
        return (adverse ? "A:" : "J:") + (nomAffiche == null ? "" : nomAffiche.toLowerCase(java.util.Locale.ROOT).trim());
    }

    /** Vrai si un camp a au moins deux emplacements sur le terrain. */
    public static boolean estCombatDouble() {
        try {
            ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
            if (battle == null) return false;
            for (var side : battle.getSides()) {
                int emplacements = 0;
                for (ClientBattleActor acteur : side.getActors()) emplacements += acteur.getActivePokemon().size();
                if (emplacements >= 2) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** Mes Pokémon sur le terrain, dans l'ordre des emplacements (null = emplacement vide / K.O.). */
    public static List<ActifDouble> getActifsJoueurDouble() {
        List<ActifDouble> r = new java.util.ArrayList<>();
        ClientBattleActor acteur = getActeurJoueur();
        if (acteur == null) return r;
        for (ActiveClientBattlePokemon actif : acteur.getActivePokemon()) {
            ClientBattlePokemon cbp = actif.getBattlePokemon();
            if (cbp == null) {
                r.add(null);
                continue;
            }
            com.cobblemon.mod.common.pokemon.Pokemon complet = null;
            try {
                for (com.cobblemon.mod.common.pokemon.Pokemon p : acteur.getPokemon()) {
                    if (p.getUuid().equals(cbp.getUuid())) {
                        complet = p;
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
            try {
                Pokemon p = convertir(cbp, complet);
                if (complet != null) {
                    // Talent et objet de ma fiche (le combat ne les donne pas toujours)
                    String talentFr = ShowdownIdMapper.talent(complet.getAbility().getName());
                    if (talentFr != null && !talentFr.equals(p.getTalent())) {
                        int pv = p.getPvActuels();
                        p = p.copieAvec(java.util.Map.of(), null, talentFr);
                        p.setPvActuels(pv);
                    }
                    if (!complet.heldItem().isEmpty()) {
                        var itemId = Registries.ITEM.getId(complet.heldItem().getItem());
                        String objetFr = itemId == null ? null : ShowdownIdMapper.objet(itemId.getPath());
                        if (objetFr != null) p.setObjet(objetFr);
                    }
                }
                r.add(new ActifDouble(p, complet, cbp.getUuid(), cleDouble(false, nomAffiche(cbp))));
            } catch (Exception e) {
                r.add(null);
            }
        }
        return r;
    }

    /** Pokémon adverses sur le terrain (tous les dresseurs d'en face), null = emplacement vide. */
    public static List<ActifDouble> getActifsAdversaireDouble() {
        List<ActifDouble> r = new java.util.ArrayList<>();
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        ClientBattleActor acteurJoueur = getActeurJoueur();
        if (battle == null || acteurJoueur == null) return r;
        for (var side : battle.getSides()) {
            if (side.getActors().contains(acteurJoueur)) continue;
            for (ClientBattleActor acteur : side.getActors()) {
                for (ActiveClientBattlePokemon actif : acteur.getActivePokemon()) {
                    ClientBattlePokemon cbp = actif.getBattlePokemon();
                    if (cbp == null) {
                        r.add(null);
                        continue;
                    }
                    try {
                        Pokemon p = convertir(cbp);
                        p.setCampAdverse(true);
                        r.add(new ActifDouble(p, null, cbp.getUuid(), cleDouble(true, nomAffiche(cbp))));
                    } catch (Exception e) {
                        r.add(null);
                    }
                }
            }
        }
        return r;
    }

    private static String nomAffiche(ClientBattlePokemon cbp) {
        try {
            return cbp.getDisplayName().getString();
        } catch (Exception e) {
            return cbp.getProperties().getSpecies();
        }
    }

    /**
     * UUID de mon Pokémon dont le jeu attend l'action (première requête sans
     * réponse), null si aucune action n'est attendue. Par réflexion pour la
     * réponse : un nom différent ne doit pas casser le mod.
     */
    public static UUID getUuidQuiChoisit() {
        try {
            ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
            if (battle == null) return null;
            List<com.cobblemon.mod.common.client.battle.SingleActionRequest> requetes = battle.getPendingActionRequests();
            if (requetes == null) return null;
            for (com.cobblemon.mod.common.client.battle.SingleActionRequest r : requetes) {
                Object reponse = null;
                try {
                    reponse = r.getClass().getMethod("getResponse").invoke(r);
                } catch (Exception ignored) {
                }
                if (reponse != null) continue;
                ActiveClientBattlePokemon concerne = r.getActivePokemon();
                if (concerne == null || concerne.getBattlePokemon() == null) continue;
                return concerne.getBattlePokemon().getUuid();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // Forme régionale / alternative vue en combat, par espèce : son talent et
    // son set Smogon ne sont pas ceux de la forme de base.
    private static final java.util.Map<String, com.cobblemon.mod.common.pokemon.FormData> FORMES =
        new java.util.concurrent.ConcurrentHashMap<>();

    private static void noterForme(String espece, com.cobblemon.mod.common.pokemon.FormData forme) {
        try {
            if (espece == null || forme == null) return;
            String nom = forme.getName();
            if (nom == null || nom.equalsIgnoreCase("normal") || nom.isBlank()) {
                FORMES.remove(espece);
                return;
            }
            FORMES.put(espece, forme);
            SmogonDataLoader.noterForme(espece, espece + nom);
        } catch (Exception ignored) {
        }
    }

    public static void oublierFormes() {
        FORMES.clear();
        SmogonDataLoader.oublierFormes();
    }

    /** Noms (anglais) des talents possibles de cette espèce, forme vue en combat comprise ; null si inconnue. */
    public static List<String> talentsPossibles(String espece) {
        if (espece == null) return null;
        List<String> r = new java.util.ArrayList<>();
        try {
            com.cobblemon.mod.common.pokemon.FormData f = FORMES.get(espece);
            if (f != null) {
                for (var a : f.getAbilities()) r.add(a.getTemplate().getName());
                if (!r.isEmpty()) return r;
            }
            Species s = com.cobblemon.mod.common.api.pokemon.PokemonSpecies.INSTANCE.getByName(espece);
            if (s == null) return null;
            for (var a : s.getAbilities()) r.add(a.getTemplate().getName());
        } catch (Exception e) {
            return null;
        }
        return r;
    }

    private static int statBase(com.cobblemon.mod.common.pokemon.FormData forme, Stats stat) {
        Integer valeur = forme.getBaseStats().get(stat);
        return valeur != null ? valeur : 0;
    }
}
