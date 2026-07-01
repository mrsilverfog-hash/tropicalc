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
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;

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

    public static Pokemon getJoueurActif() {
        ClientBattleActor acteur = getActeurJoueur();
        if (acteur == null) return null;
        return premierActif(acteur);
    }

    public static Pokemon getAdversaireActif() {
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        ClientBattleActor acteurJoueur = getActeurJoueur();
        if (battle == null || acteurJoueur == null) return null;
        for (var side : battle.getSides()) {
            if (!side.getActors().contains(acteurJoueur)) {
                for (ClientBattleActor acteur : side.getActors()) {
                    Pokemon p = premierActif(acteur);
                    if (p != null) return p;
                }
            }
        }
        return null;
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
        Pokemon p = convertir(cbp);
        com.tropimon.tropicalc.TropiCalcClient.LOGGER.info(
            "[TropiCalc-diag] convertir: espece={} pvMax={} pvActuels={} isFlat={} hpValue={} maxHp={}",
            cbp.getSpecies().showdownId(), p.getPvMax(), p.getPvActuels(),
            cbp.isHpFlat(), cbp.getHpValue(), cbp.getMaxHp());
        return p;
    }

    private static Pokemon convertir(ClientBattlePokemon cbp) {
        Species espece = cbp.getSpecies();
        PokemonProperties props = cbp.getProperties();

        PokemonType type1 = ShowdownIdMapper.type(espece.getPrimaryType().getName());
        PokemonType type2 = espece.getSecondaryType() != null
            ? ShowdownIdMapper.type(espece.getSecondaryType().getName()) : null;

        Pokemon.Builder builder = Pokemon.builder(props.getSpecies(), cbp.getLevel(), type1, type2)
            .statBase(Stat.PV, statBase(espece, Stats.HP))
            .statBase(Stat.ATTAQUE, statBase(espece, Stats.ATTACK))
            .statBase(Stat.DEFENSE, statBase(espece, Stats.DEFENCE))
            .statBase(Stat.ATTAQUE_SPE, statBase(espece, Stats.SPECIAL_ATTACK))
            .statBase(Stat.DEFENSE_SPE, statBase(espece, Stats.SPECIAL_DEFENCE))
            .statBase(Stat.VITESSE, statBase(espece, Stats.SPEED))
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

        PokemonType type1 = ShowdownIdMapper.type(espece.getPrimaryType().getName());
        PokemonType type2 = espece.getSecondaryType() != null
            ? ShowdownIdMapper.type(espece.getSecondaryType().getName()) : null;

        Pokemon.Builder builder = Pokemon.builder(espece.showdownId(), p.getLevel(), type1, type2)
            .statBase(Stat.PV, statBase(espece, Stats.HP))
            .statBase(Stat.ATTAQUE, statBase(espece, Stats.ATTACK))
            .statBase(Stat.DEFENSE, statBase(espece, Stats.DEFENCE))
            .statBase(Stat.ATTAQUE_SPE, statBase(espece, Stats.SPECIAL_ATTACK))
            .statBase(Stat.DEFENSE_SPE, statBase(espece, Stats.SPECIAL_DEFENCE))
            .statBase(Stat.VITESSE, statBase(espece, Stats.SPEED));

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

        // Utilise getEffectiveNature() pour tenir compte des Menthes
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
        pokemon.setPvActuels(p.getCurrentHealth());

        com.tropimon.tropicalc.TropiCalcClient.LOGGER.info(
            "[TropiCalc-diag] PokemonComplet: espece={} pvMax={} atkEV={} atkIV={} nature={} talent={} objet={}",
            espece.showdownId(), pokemon.getPvMax(),
            p.getEvs().getOrDefault(Stats.ATTACK), p.getIvs().getOrDefault(Stats.ATTACK),
            p.getEffectiveNature().getName().getPath(), p.getAbility().getName(), pokemon.getTalent());

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

    private static int statBase(Species espece, Stats stat) {
        Integer valeur = espece.getBaseStats().get(stat);
        return valeur != null ? valeur : 0;
    }
}
