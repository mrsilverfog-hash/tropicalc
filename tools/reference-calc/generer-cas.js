// Génère src/test/resources/cas-reference-showdown.json : des situations de
// combat calculées par le calculateur officiel de Showdown (@smogon/calc),
// qui sert de référence aux tests du calcul de dégâts de TropiCalc.
//
// Utilisation (Node 18+) :
//   cd tools/reference-calc && npm install && node generer-cas.js
//
// Chaque cas contient tout ce qu'il faut pour le reconstruire côté Java
// (stats de base, types, poids, capacité...) : le test n'a ainsi besoin ni
// de Cobblemon ni de Minecraft. Pour ajouter un cas, l'ajouter à la liste
// CAS ci-dessous puis relancer le script.

const fs = require('fs');
const path = require('path');
const { calculate, Generations, Pokemon, Move, Field } = require('@smogon/calc');

const gen = Generations.get(9);

const toId = (s) => (s || '').toLowerCase().replace(/[^a-z0-9]/g, '');

// Raccourcis pour des sets courants
const ATK = { nature: 'Adamant', evs: { atk: 252, spe: 252, hp: 4 } };
const SPA = { nature: 'Modest', evs: { spa: 252, spe: 252, hp: 4 } };
const PHYS_DEF = { nature: 'Impish', evs: { hp: 252, def: 252, spd: 4 } };
const SPEC_DEF = { nature: 'Calm', evs: { hp: 252, spd: 252, def: 4 } };

// [nom, attaquant, défenseur, capacité, options]
// attaquant/défenseur : [espèce, set]
// options : { field: {...}, crit: bool, niveau: n }
const CAS = [
    // --- Base : STAB, efficacité, natures, niveaux ---
    ['STAB neutre', ['Garchomp', ATK], ['Great Tusk', PHYS_DEF], 'Earthquake'],
    ['Super efficace x4', ['Garchomp', ATK], ['Heatran', PHYS_DEF], 'Earthquake'],
    ['Pas très efficace', ['Garchomp', ATK], ['Corviknight', PHYS_DEF], 'Dragon Claw'],
    ['Double résistance', ['Great Tusk', ATK], ['Corviknight', PHYS_DEF], 'Close Combat'],
    ['Spéciale sans STAB', ['Gholdengo', SPA], ['Dragonite', SPEC_DEF], 'Focus Blast'],
    ['Nature baissée', ['Dragapult', { nature: 'Timid', evs: { spa: 252, spe: 252 } }], ['Garchomp', { evs: { hp: 4 } }], 'Shadow Ball'],
    ['Niveau 50', ['Garchomp', ATK], ['Gholdengo', PHYS_DEF], 'Earthquake', { niveau: 50 }],
    ['EV et IV partiels', ['Kingambit', { nature: 'Adamant', evs: { hp: 212, atk: 252, spe: 44 }, ivs: { spe: 0 } }],
        ['Toxapex', { nature: 'Bold', evs: { hp: 252, def: 252 } }], 'Kowtow Cleave'],
    ['Ténèbres sur Spectre', ['Kingambit', ATK], ['Gengar', SPEC_DEF], 'Sucker Punch'],
    ['Immunité Spectre', ['Great Tusk', ATK], ['Gengar', SPEC_DEF], 'Close Combat'],
    ['Immunité Vol', ['Garchomp', ATK], ['Corviknight', PHYS_DEF], 'Earthquake'],
    ['Lyophilisation sur Eau', ['Kyurem', SPA], ['Toxapex', SPEC_DEF], 'Freeze-Dry'],

    // --- Objets ---
    ['Bandeau Choix', ['Garchomp', { ...ATK, item: 'Choice Band' }], ['Heatran', PHYS_DEF], 'Earthquake'],
    ['Lunettes Choix', ['Gholdengo', { ...SPA, item: 'Choice Specs' }], ['Dragonite', SPEC_DEF], 'Make It Rain'],
    ['Orbe Vie', ['Dragapult', { ...SPA, item: 'Life Orb' }], ['Gholdengo', SPEC_DEF], 'Shadow Ball'],
    ['Ceinture Pro SE', ['Great Tusk', { ...ATK, item: 'Expert Belt' }], ['Kingambit', PHYS_DEF], 'Close Combat'],
    ['Ceinture Pro neutre', ['Great Tusk', { ...ATK, item: 'Expert Belt' }], ['Dragapult', PHYS_DEF], 'Headlong Rush'],
    ['Charbon', ['Cinderace', { ...ATK, item: 'Charcoal' }], ['Corviknight', PHYS_DEF], 'Pyro Ball'],
    ['Eau Mystique', ['Primarina', { ...SPA, item: 'Mystic Water' }], ['Heatran', SPEC_DEF], 'Surf'],
    ['Bandeau Muscle', ['Iron Hands', { ...ATK, item: 'Muscle Band' }], ['Corviknight', PHYS_DEF], 'Drain Punch'],
    ['Lunettes Sages', ['Iron Moth', { ...SPA, item: 'Wise Glasses' }], ['Ting-Lu', SPEC_DEF], 'Fiery Dance'],
    ['Veste de Combat', ['Gholdengo', SPA], ['Iron Treads', { ...SPEC_DEF, item: 'Assault Vest' }], 'Shadow Ball'],
    ['Évoluroc', ['Garchomp', ATK], ['Dusclops', { ...PHYS_DEF, item: 'Eviolite' }], 'Earthquake'],

    // --- Météo ---
    ['Soleil Feu', ['Heatran', SPA], ['Corviknight', SPEC_DEF], 'Flamethrower', { field: { weather: 'Sun' } }],
    ['Soleil Eau', ['Primarina', SPA], ['Heatran', SPEC_DEF], 'Surf', { field: { weather: 'Sun' } }],
    ['Pluie Eau', ['Primarina', SPA], ['Corviknight', SPEC_DEF], 'Surf', { field: { weather: 'Rain' } }],
    ['Pluie Feu', ['Heatran', SPA], ['Corviknight', SPEC_DEF], 'Flamethrower', { field: { weather: 'Rain' } }],
    ['Sable Déf. Spé. Roche', ['Primarina', SPA], ['Tyranitar', SPEC_DEF], 'Moonblast', { field: { weather: 'Sand' } }],
    ['Neige Déf. Glace', ['Garchomp', ATK], ['Baxcalibur', PHYS_DEF], 'Earthquake', { field: { weather: 'Snow' } }],
    ['Ball\'Météo soleil', ['Ninetales', SPA], ['Corviknight', SPEC_DEF], 'Weather Ball', { field: { weather: 'Sun' } }],

    // --- Champs ---
    ['Champ Électrifié', ['Iron Hands', ATK], ['Corviknight', PHYS_DEF], 'Wild Charge', { field: { terrain: 'Electric' } }],
    ['Champ Herbu Séisme', ['Garchomp', ATK], ['Heatran', PHYS_DEF], 'Earthquake', { field: { terrain: 'Grassy' } }],
    ['Champ Psychique', ['Iron Boulder', ATK], ['Great Tusk', PHYS_DEF], 'Zen Headbutt', { field: { terrain: 'Psychic' } }],
    ['Champ Brumeux Dragon', ['Dragapult', SPA], ['Garchomp', SPEC_DEF], 'Draco Meteor', { field: { terrain: 'Misty' } }],
    ['Champ sans effet en vol', ['Corviknight', ATK], ['Iron Hands', PHYS_DEF], 'Brave Bird', { field: { terrain: 'Grassy' } }],

    // --- Écrans, critiques ---
    ['Protection', ['Garchomp', ATK], ['Heatran', PHYS_DEF], 'Earthquake', { field: { defenderSide: { isReflect: true } } }],
    ['Mur Lumière', ['Gholdengo', SPA], ['Dragonite', SPEC_DEF], 'Shadow Ball', { field: { defenderSide: { isLightScreen: true } } }],
    ['Critique', ['Garchomp', ATK], ['Great Tusk', PHYS_DEF], 'Earthquake', { crit: true }],
    ['Critique ignore Protection', ['Garchomp', ATK], ['Heatran', PHYS_DEF], 'Earthquake', { crit: true, field: { defenderSide: { isReflect: true } } }],

    // --- Stages, statut ---
    ['Attaque +2', ['Kingambit', { ...ATK, boosts: { atk: 2 } }], ['Corviknight', PHYS_DEF], 'Kowtow Cleave'],
    ['Défense -1', ['Garchomp', ATK], ['Great Tusk', { ...PHYS_DEF, boosts: { def: -1 } }], 'Earthquake'],
    ['Défense +2', ['Garchomp', ATK], ['Heatran', { ...PHYS_DEF, boosts: { def: 2 } }], 'Earthquake'],
    ['Critique ignore Déf +2', ['Garchomp', ATK], ['Heatran', { ...PHYS_DEF, boosts: { def: 2 } }], 'Earthquake', { crit: true }],
    ['Critique ignore Att -1', ['Garchomp', { ...ATK, boosts: { atk: -1 } }], ['Heatran', PHYS_DEF], 'Earthquake', { crit: true }],
    ['Brûlure', ['Garchomp', { ...ATK, status: 'brn' }], ['Heatran', PHYS_DEF], 'Earthquake'],

    // --- Talents offensifs ---
    ['Adaptabilité', ['Porygon-Z', { ...SPA, ability: 'Adaptability' }], ['Corviknight', SPEC_DEF], 'Hyper Beam'],
    ['Technicien', ['Scizor', { ...ATK, ability: 'Technician' }], ['Gholdengo', PHYS_DEF], 'Bullet Punch'],
    ['Force Pure', ['Azumarill', { ...ATK, ability: 'Huge Power' }], ['Dragonite', PHYS_DEF], 'Play Rough'],
    ['Cran brûlé', ['Ursaluna', { ...ATK, ability: 'Guts', status: 'brn' }], ['Dragapult', PHYS_DEF], 'Headlong Rush'],
    ['Griffe Dure', ['Meowscarada', { ...ATK, ability: 'Tough Claws' }], ['Gholdengo', PHYS_DEF], 'Flower Trick'],
    ['Poing de Fer', ['Iron Hands', { ...ATK, ability: 'Iron Fist' }], ['Corviknight', PHYS_DEF], 'Thunder Punch'],
    ['Prognathe', ['Tyranitar', { ...ATK, ability: 'Strong Jaw' }], ['Gholdengo', PHYS_DEF], 'Crunch'],
    ['Tranchant', ['Gallade', { ...ATK, ability: 'Sharpness' }], ['Kingambit', PHYS_DEF], 'Sacred Sword'],
    ['Agitation', ['Dragonite', { ...ATK, ability: 'Hustle' }], ['Corviknight', PHYS_DEF], 'Outrage'],
    ['Lentiteintée', ['Venomoth', { ...SPA, ability: 'Tinted Lens' }], ['Heatran', SPEC_DEF], 'Bug Buzz'],
    ['Force Sable', ['Excadrill', { ...ATK, ability: 'Sand Force' }], ['Corviknight', PHYS_DEF], 'Iron Head', { field: { weather: 'Sand' } }],
    ['Transistor', ['Regieleki', { ...SPA, ability: 'Transistor' }], ['Corviknight', SPEC_DEF], 'Thunderbolt'],
    ['Dent de Dragon', ['Regidrago', { ...SPA, ability: 'Dragon\'s Maw' }], ['Corviknight', SPEC_DEF], 'Draco Meteor'],
    ['Porte-Roche', ['Garganacl', { ...ATK, ability: 'Rocky Payload' }], ['Corviknight', PHYS_DEF], 'Stone Edge'],
    ['Expert Acier', ['Dhelmise', { ...ATK, ability: 'Steelworker' }], ['Corviknight', PHYS_DEF], 'Anchor Shot'],
    ['Aquabulle attaque', ['Araquanid', { ...ATK, ability: 'Water Bubble' }], ['Corviknight', PHYS_DEF], 'Liquidation'],
    ['Peau Féérique', ['Sylveon', { ...SPA, ability: 'Pixilate' }], ['Dragonite', SPEC_DEF], 'Hyper Voice'],
    ['Peau Céleste', ['Salamence', { ...ATK, ability: 'Aerilate' }], ['Great Tusk', PHYS_DEF], 'Double-Edge'],
    ['Brasier sous un tiers', ['Cinderace', { ...ATK, ability: 'Blaze', curHP: 30 }], ['Corviknight', PHYS_DEF], 'Pyro Ball'],
    ['Téméraire', ['Staraptor', { ...ATK, ability: 'Reckless' }], ['Great Tusk', PHYS_DEF], 'Brave Bird'],
    ['Méga Blaster', ['Clawitzer', { ...SPA, ability: 'Mega Launcher' }], ['Corviknight', SPEC_DEF], 'Water Pulse'],
    ['Punk Rock', ['Toxtricity', { ...SPA, ability: 'Punk Rock' }], ['Corviknight', SPEC_DEF], 'Overdrive'],
    ['Épée du Fléau', ['Chien-Pao', { ...ATK, ability: 'Sword of Ruin' }], ['Corviknight', PHYS_DEF], 'Icicle Crash'],
    ['Perles du Fléau', ['Chi-Yu', { ...SPA, ability: 'Beads of Ruin' }], ['Corviknight', SPEC_DEF], 'Dark Pulse'],
    ['Tablettes du Fléau (défenseur)', ['Garchomp', ATK], ['Wo-Chien', { ...PHYS_DEF, ability: 'Tablets of Ruin' }], 'Earthquake'],
    ['Urne du Fléau (défenseur)', ['Gholdengo', SPA], ['Ting-Lu', { ...SPEC_DEF, ability: 'Vessel of Ruin' }], 'Shadow Ball'],

    // --- Talents défensifs ---
    ['Multiécaille', ['Garchomp', ATK], ['Dragonite', { ...PHYS_DEF, ability: 'Multiscale' }], 'Stone Edge'],
    ['Filtre', ['Garchomp', ATK], ['Heatran', { ...PHYS_DEF, ability: 'Filter' }], 'Earthquake'],
    ['Isograisse', ['Cinderace', ATK], ['Mamoswine', { ...PHYS_DEF, ability: 'Thick Fat' }], 'Pyro Ball'],
    ['Toison Épaisse', ['Garchomp', ATK], ['Persian-Alola', { ...PHYS_DEF, ability: 'Fur Coat' }], 'Earthquake'],
    ['Écailles Glacées', ['Gholdengo', SPA], ['Frosmoth', { ...SPEC_DEF, ability: 'Ice Scales' }], 'Shadow Ball'],
    ['Boule de Poils contact', ['Iron Hands', ATK], ['Dachsbun', { ...PHYS_DEF, ability: 'Fluffy' }], 'Drain Punch'],
    ['Lévitation', ['Garchomp', ATK], ['Rotom-Wash', { ...PHYS_DEF, ability: 'Levitate' }], 'Earthquake'],
    ['Ignifugé', ['Heatran', SPA], ['Bronzong', { ...SPEC_DEF, ability: 'Heatproof' }], 'Flamethrower'],
    ['Aquabulle défense', ['Heatran', SPA], ['Araquanid', { ...SPEC_DEF, ability: 'Water Bubble' }], 'Flamethrower'],

    // --- Capacités à puissance variable ---
    ['Sabotage sur objet', ['Kingambit', ATK], ['Gholdengo', { ...PHYS_DEF, item: 'Leftovers' }], 'Knock Off'],
    ['Sabotage sans objet', ['Kingambit', ATK], ['Gholdengo', PHYS_DEF], 'Knock Off'],
    ['Acrobatie sans objet', ['Corviknight', ATK], ['Great Tusk', PHYS_DEF], 'Acrobatics'],
    ['Façade brûlé', ['Ursaluna', { ...ATK, status: 'brn' }], ['Corviknight', PHYS_DEF], 'Facade'],
    ['Châtiment sur statut', ['Gengar', SPA], ['Gholdengo', { ...SPEC_DEF, status: 'par' }], 'Hex'],
    ['Saumure sous 50 %', ['Primarina', SPA], ['Heatran', { ...SPEC_DEF, curHP: 40 }], 'Brine'],
    ['Choc Venin sur empoisonné', ['Toxapex', { ...SPA, ability: 'Regenerator' }], ['Great Tusk', { ...SPEC_DEF, status: 'psn' }], 'Venoshock'],
    ['Tacle Lourd', ['Copperajah', ATK], ['Gholdengo', PHYS_DEF], 'Heavy Slam'],
    ['Balayage', ['Great Tusk', ATK], ['Kingambit', PHYS_DEF], 'Low Kick'],
    ['Gyroballe', ['Ferrothorn', { nature: 'Brave', evs: { hp: 252, atk: 252 }, ivs: { spe: 0 } }], ['Dragapult', PHYS_DEF], 'Gyro Ball'],
    ['Boule Élek', ['Regieleki', SPA], ['Corviknight', SPEC_DEF], 'Electro Ball'],
    ['Éruption PV pleins', ['Typhlosion', SPA], ['Corviknight', SPEC_DEF], 'Eruption'],
    ['Éruption demi PV', ['Typhlosion', { ...SPA, curHP: 50 }], ['Corviknight', SPEC_DEF], 'Eruption'],
    ['Force Ajoutée +2/+2', ['Espeon', { ...SPA, boosts: { spa: 2, spd: 2 } }], ['Great Tusk', SPEC_DEF], 'Stored Power'],
    ['Big Splash', ['Corviknight', { nature: 'Impish', evs: { hp: 252, def: 252 } }], ['Great Tusk', PHYS_DEF], 'Body Press'],
    ['Tricherie', ['Grimmsnarl', { nature: 'Careful', evs: { hp: 252, spd: 252 } }], ['Dragonite', { ...ATK, boosts: { atk: 1 } }], 'Foul Play'],
    ['Choc Psy', ['Alakazam', SPA], ['Blissey', SPEC_DEF], 'Psyshock'],
    ['Monte-Tension champ', ['Iron Hands', SPA], ['Corviknight', SPEC_DEF], 'Rising Voltage', { field: { terrain: 'Electric' } }],
    ['Vaste Pouvoir champ', ['Indeedee', SPA], ['Great Tusk', SPEC_DEF], 'Expanding Force', { field: { terrain: 'Psychic' } }],
    ['Lame Psychique champ', ['Iron Leaves', ATK], ['Great Tusk', PHYS_DEF], 'Psyblade', { field: { terrain: 'Electric' } }],
    ['Multi-Toxik sur empoisonné', ['Overqwil', ATK], ['Great Tusk', { ...PHYS_DEF, status: 'psn' }], 'Barb Barrage'],
    ['Façade sans statut', ['Ursaluna', ATK], ['Corviknight', PHYS_DEF], 'Facade'],
    ['Ball\'Météo pluie', ['Pelipper', SPA], ['Heatran', SPEC_DEF], 'Weather Ball', { field: { weather: 'Rain' } }],
    ['Turbo-Charge super efficace', ['Koraidon', ATK], ['Kingambit', PHYS_DEF], 'Collision Course'],
    ['Frappe Atlas', ['Blissey', SPEC_DEF], ['Corviknight', PHYS_DEF], 'Seismic Toss'],

    // --- Talents reclassés (vérification de l'étape de chaque bonus) ---
    ['Pouls Orichalque soleil', ['Koraidon', ATK], ['Corviknight', PHYS_DEF], 'Flare Blitz', { field: { weather: 'Sun' } }],
    ['Moteur Hadron champ', ['Miraidon', SPA], ['Corviknight', SPEC_DEF], 'Electro Drift', { field: { terrain: 'Electric' } }],
    ['Proto-Synthèse défenseur', ['Garchomp', ATK], ['Great Tusk', { nature: 'Impish', evs: { hp: 252, def: 252 } }], 'Earthquake', { field: { weather: 'Sun' } }],
    ['Moteur Quark attaquant', ['Iron Valiant', SPA], ['Corviknight', SPEC_DEF], 'Moonblast', { field: { terrain: 'Electric' } }],
    ['Aura Sombre', ['Yveltal', SPA], ['Corviknight', SPEC_DEF], 'Dark Pulse'],
    ['Peau Sèche contre Feu', ['Heatran', SPA], ['Toxicroak', { ...SPEC_DEF, ability: 'Dry Skin' }], 'Flamethrower'],
    ['Sel Purificateur', ['Gholdengo', SPA], ['Garganacl', SPEC_DEF], 'Shadow Ball'],
    ['Boule de Poils contre Feu', ['Heatran', SPA], ['Dachsbun', { ...SPEC_DEF, ability: 'Fluffy' }], 'Flamethrower'],
    ['Sniper critique', ['Kingdra', { ...SPA, ability: 'Sniper' }], ['Corviknight', SPEC_DEF], 'Hydro Pump', { crit: true }],
    ['Cran sur spéciale', ['Heracross', { ...SPA, ability: 'Guts', status: 'par' }], ['Corviknight', SPEC_DEF], 'Focus Blast'],
    ['Entêtement', ['Darmanitan-Galar', { ...ATK, ability: 'Gorilla Tactics' }], ['Corviknight', PHYS_DEF], 'Icicle Crash'],
    ['Force Soleil', ['Houndoom', { ...SPA, ability: 'Solar Power' }], ['Corviknight', SPEC_DEF], 'Fire Blast', { field: { weather: 'Sun' } }],
    ['Rage Poison', ['Zangoose', { ...ATK, ability: 'Toxic Boost', status: 'psn' }], ['Corviknight', PHYS_DEF], 'Close Combat'],
    ['Écaille Spéciale', ['Garchomp', ATK], ['Dragonite', { ...PHYS_DEF, ability: 'Marvel Scale', status: 'par' }], 'Stone Edge'],
    ['Sans Limite', ['Landorus', { ...SPA, ability: 'Sheer Force' }], ['Great Tusk', SPEC_DEF], 'Sludge Wave'],
    ['Lucidité défenseur', ['Kingambit', { ...ATK, boosts: { atk: 2 } }], ['Dondozo', { ...PHYS_DEF, ability: 'Unaware' }], 'Kowtow Cleave'],
    ['Big Splash +1 Déf', ['Corviknight', { nature: 'Impish', evs: { hp: 252, def: 252 }, boosts: { def: 1 } }], ['Great Tusk', PHYS_DEF], 'Body Press'],
    ['Agitation brûlé', ['Dragonite', { ...ATK, ability: 'Hustle', status: 'brn' }], ['Great Tusk', PHYS_DEF], 'Outrage'],
    ['Bandeau + Griffe Dure + Sabotage', ['Meowscarada', { ...ATK, ability: 'Tough Claws', item: 'Choice Band' }], ['Great Tusk', { ...PHYS_DEF, item: 'Leftovers' }], 'Knock Off'],
    ['Orbe Vie + Technicien', ['Scizor', { ...ATK, ability: 'Technician', item: 'Life Orb' }], ['Great Tusk', PHYS_DEF], 'Bullet Punch'],
    ['Gant de Boxe + Poing de Fer', ['Iron Hands', { ...ATK, ability: 'Iron Fist', item: 'Punching Glove' }], ['Corviknight', PHYS_DEF], 'Thunder Punch'],
    ['Analyste contre plus rapide', ['Magnezone', { ...SPA, ability: 'Analytic' }], ['Dragapult', SPEC_DEF], 'Flash Cannon'],
    ['Analyste contre plus lent', ['Magnezone', { ...SPA, ability: 'Analytic' }], ['Toxapex', { ...SPEC_DEF, ability: 'Regenerator' }], 'Flash Cannon'],
    ['Protection + Orbe Vie', ['Garchomp', { ...ATK, item: 'Life Orb' }], ['Great Tusk', PHYS_DEF], 'Earthquake', { field: { defenderSide: { isReflect: true } } }],
    ['Ombre Nocturne', ['Dusclops', PHYS_DEF], ['Great Tusk', PHYS_DEF], 'Night Shade'],
];

const META = {
    Sun: 'SOLEIL', Rain: 'PLUIE', Sand: 'SABLE', Snow: 'NEIGE',
    'Harsh Sunshine': 'SOLEIL_INTENSE', 'Heavy Rain': 'PLUIE_INTENSE',
};
const CHAMPS = { Electric: 'ELECTRIQUE', Grassy: 'HERBU', Psychic: 'PSYCHIQUE', Misty: 'BRUMEUX' };

function construire(espece, set, niveau) {
    // boostedStat 'auto' : Protosynthèse / Moteur Quark s'activent d'eux-mêmes
    // sous soleil / Champ Électrifié, comme en vrai combat (sinon le
    // calculateur Showdown les laisse éteints).
    const opts = { level: niveau, boostedStat: 'auto', ...set };
    delete opts.curHP;
    const p = new Pokemon(gen, espece, opts);
    if (set.curHP !== undefined) {
        // Pourcentage des PV max demandé
        p.originalCurHP = Math.floor(p.maxHP() * set.curHP / 100);
    }
    return p;
}

function decrire(p, set) {
    const s = gen.species.get(toId(p.name));
    return {
        espece: toId(p.name),
        niveau: p.level,
        types: p.types.map(toId),
        statsBase: s.baseStats,
        evs: p.evs,
        ivs: p.ivs,
        nature: toId(p.nature),
        talent: toId(p.ability),
        objet: p.item ? toId(p.item) : null,
        boosts: p.boosts,
        statut: p.status || '',
        pvActuels: p.curHP(),
        poidsKg: s.weightkg,
        statsShowdown: p.stats, // pour diagnostiquer un écart de stat
    };
}

const sortie = [];
for (const [nom, [espA, setA], [espD, setD], nomCapacite, options = {}] of CAS) {
    const niveau = options.niveau || 100;
    const attaquant = construire(espA, setA, niveau);
    const defenseur = construire(espD, setD, niveau);
    const capacite = new Move(gen, nomCapacite, { isCrit: !!options.crit });
    const champ = new Field(options.field || {});
    const r = calculate(gen, attaquant, defenseur, capacite, champ);
    const degats = typeof r.damage === 'number' ? [r.damage] : r.damage;
    const ecrans = (options.field && options.field.defenderSide) || {};
    const donnees = gen.moves.get(toId(nomCapacite));
    sortie.push({
        nom,
        attaquant: decrire(attaquant, setA),
        defenseur: decrire(defenseur, setD),
        capacite: {
            id: toId(nomCapacite),
            type: toId(donnees.type),
            categorie: donnees.category,
            puissance: donnees.basePower,
        },
        meteo: options.field && options.field.weather ? META[options.field.weather] : 'AUCUNE',
        champ: options.field && options.field.terrain ? CHAMPS[options.field.terrain] : 'AUCUN',
        protection: !!ecrans.isReflect,
        murLumiere: !!ecrans.isLightScreen,
        critique: !!options.crit,
        degatsAttendus: degats.length === 1 ? Array(16).fill(degats[0]) : degats,
        description: (() => { try { return r.desc(); } catch (e) { return ''; } })(),
    });
}

// ---------------------------------------------------------------------
// Vitesse : même principe, comparée à getFinalSpeed de Showdown.
// [nom, espèce, set, options] ; options : { weather, terrain, tailwind }
// ---------------------------------------------------------------------
const { getFinalSpeed } = require('@smogon/calc/dist/mechanics/util');
const SCARF = { nature: 'Jolly', evs: { spe: 252, atk: 252 }, item: 'Choice Scarf' };
const RAPIDE = { nature: 'Timid', evs: { spe: 252, spa: 252 } };
const CAS_VITESSE = [
    ['Neutre 0 EV', 'Garchomp', { nature: 'Adamant', evs: { atk: 252, hp: 252 } }],
    ['252 EV nature +Vit', 'Garchomp', { nature: 'Jolly', evs: { spe: 252 } }],
    ['Nature -Vit, 0 IV', 'Ferrothorn', { nature: 'Relaxed', evs: { hp: 252 }, ivs: { spe: 0 } }],
    ['Mouchoir Choix', 'Garchomp', SCARF],
    ['Mouchoir + stage +1', 'Garchomp', { ...SCARF, boosts: { spe: 1 } }],
    ['Stage +1 valeur impaire', 'Volcarona', { nature: 'Modest', evs: { spe: 252 }, boosts: { spe: 1 } }],
    ['Stage +2', 'Dragonite', { nature: 'Adamant', evs: { spe: 252 }, boosts: { spe: 2 } }],
    ['Stage -1', 'Gholdengo', { ...RAPIDE, boosts: { spe: -1 } }],
    ['Stage -2', 'Kingambit', { nature: 'Adamant', evs: { spe: 4 }, boosts: { spe: -2 } }],
    ['Paralysie', 'Dragapult', { ...RAPIDE, status: 'par' }],
    ['Paralysie + Mouchoir', 'Dragapult', { ...RAPIDE, item: 'Choice Scarf', status: 'par' }],
    ['Paralysie + stage +1', 'Cinderace', { nature: 'Jolly', evs: { spe: 252 }, status: 'par', boosts: { spe: 1 } }],
    ['Vent Arrière', 'Gholdengo', RAPIDE, { tailwind: true }],
    ['Vent Arrière + Mouchoir', 'Gholdengo', { ...RAPIDE, item: 'Choice Scarf' }, { tailwind: true }],
    ['Vent Arrière + paralysie', 'Gholdengo', { ...RAPIDE, status: 'par' }, { tailwind: true }],
    ['Chlorophylle soleil', 'Venusaur', { nature: 'Modest', evs: { spe: 252 }, ability: 'Chlorophyll' }, { weather: 'Sun' }],
    ['Glissade pluie', 'Barraskewda', { nature: 'Adamant', evs: { spe: 252 }, ability: 'Swift Swim' }, { weather: 'Rain' }],
    ['Baigne Sable', 'Excadrill', { nature: 'Jolly', evs: { spe: 252 }, ability: 'Sand Rush' }, { weather: 'Sand' }],
    ['Chasse-Neige', 'Beartic', { nature: 'Jolly', evs: { spe: 252 }, ability: 'Slush Rush' }, { weather: 'Snow' }],
    ['Glissade + Mouchoir + Vent Arrière', 'Kingdra', { ...RAPIDE, ability: 'Swift Swim', item: 'Choice Scarf' }, { weather: 'Rain', tailwind: true }],
    ['Pied Véloce sous statut', 'Ursaring', { nature: 'Jolly', evs: { spe: 252 }, ability: 'Quick Feet', status: 'par' }],
    ['Proto-Synthèse Vitesse', 'Flutter Mane', { nature: 'Timid', evs: { spe: 252, hp: 252 } }, { weather: 'Sun' }],
    ['Moteur Quark Vitesse', 'Iron Bundle', { nature: 'Timid', evs: { spe: 252, spa: 252 } }, { terrain: 'Electric' }],
    ['Proto-Synthèse hors Vitesse', 'Great Tusk', { nature: 'Jolly', evs: { atk: 252, spe: 252 } }, { weather: 'Sun' }],
    ['Niveau 50 Mouchoir', 'Dragapult', { ...RAPIDE, item: 'Choice Scarf' }, { niveau: 50 }],
];

const sortieVitesse = [];
for (const [nom, espece, set, options = {}] of CAS_VITESSE) {
    const p = construire(espece, set, options.niveau || 100);
    const champ = new Field({ weather: options.weather, terrain: options.terrain,
        attackerSide: { isTailwind: !!options.tailwind } });
    sortieVitesse.push({
        nom,
        pokemon: decrire(p, set),
        meteo: options.weather ? META[options.weather] : 'AUCUNE',
        champ: options.terrain ? CHAMPS[options.terrain] : 'AUCUN',
        ventArriere: !!options.tailwind,
        vitesseAttendue: getFinalSpeed(gen, p, champ, champ.attackerSide),
    });
}
const fichierVitesse = path.join(__dirname, '..', '..', 'src', 'test', 'resources', 'cas-vitesse-showdown.json');
fs.writeFileSync(fichierVitesse, JSON.stringify(sortieVitesse, null, 1) + '\n');
console.log(`${sortieVitesse.length} cas de vitesse écrits dans ${fichierVitesse}`);

const fichier = path.join(__dirname, '..', '..', 'src', 'test', 'resources', 'cas-reference-showdown.json');
fs.mkdirSync(path.dirname(fichier), { recursive: true });
fs.writeFileSync(fichier, JSON.stringify(sortie, null, 1) + '\n');
console.log(`${sortie.length} cas écrits dans ${fichier}`);
