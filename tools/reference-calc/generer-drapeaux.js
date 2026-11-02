// Génère DrapeauxCapacites.java (TropiCalc et RandomPvp) depuis les données
// de @smogon/calc : contact, poing, morsure, son, tranchant, pulsation,
// balle, vent, effet secondaire (Sans Limite), recul, chute, priorité.
// Usage : node generer-drapeaux.js
const fs = require('fs');
const path = require('path');
const { Generations } = require('@smogon/calc');
const g = Generations.get(9);

const sets = { CONTACT: [], POING: [], MORSURE: [], SON: [], TRANCHANT: [], PULSATION: [],
  BALLE: [], VENT: [], EFFET_SECONDAIRE: [], CHUTE: [], RECUL_PV_MAX: [] };
const recul = [];
const priorite = [];
for (const m of g.moves) {
  const f = m.flags || {};
  if (f.contact) sets.CONTACT.push(m.id);
  if (f.punch) sets.POING.push(m.id);
  if (f.bite) sets.MORSURE.push(m.id);
  if (f.sound) sets.SON.push(m.id);
  if (f.slicing) sets.TRANCHANT.push(m.id);
  if (f.pulse) sets.PULSATION.push(m.id);
  if (f.bullet) sets.BALLE.push(m.id);
  if (f.wind) sets.VENT.push(m.id);
  if (m.secondaries) sets.EFFET_SECONDAIRE.push(m.id);
  if (m.hasCrashDamage) sets.CHUTE.push(m.id);
  if (m.mindBlownRecoil) sets.RECUL_PV_MAX.push(m.id);
  if (m.recoil) recul.push([m.id, m.recoil[0], m.recoil[1]]);
  if (m.priority) priorite.push([m.id, m.priority]);
}

function javaSet(nom, ids) {
  ids = [...new Set(ids)].sort();
  const lignes = [];
  let cur = '       ';
  for (const id of ids) {
    const t = ` "${id}",`;
    if (cur.length + t.length > 100) { lignes.push(cur); cur = '       '; }
    cur += t;
  }
  lignes.push(cur.replace(/,$/, ''));
  return `    public static final Set<String> ${nom} = Set.of(\n${lignes.join('\n')}\n    );\n`;
}
function javaMap(nom, entrees, valeur) {
  const lignes = entrees.sort((a, b) => a[0].localeCompare(b[0]))
    .map(e => `        Map.entry("${e[0]}", ${valeur(e)})`);
  return `    public static final Map<String, ${nom === 'RECUL' ? 'int[]' : 'Integer'}> ${nom} = Map.ofEntries(\n${lignes.join(',\n')}\n    );\n`;
}

for (const mod of ['tropicalc', 'randompvp']) {
  let src = `package com.tropimon.${mod}.calc;

import java.util.Map;
import java.util.Set;

/**
 * Drapeaux des capacités, GÉNÉRÉS depuis les données de @smogon/calc
 * (tools/reference-calc/generer-drapeaux.js). Ne pas modifier à la main :
 * relancer le script. Remplace les listes écrites à la main, incomplètes
 * (Poing de Feu absent de Sans Limite, Nœud Herbe compté au contact...).
 */
public final class DrapeauxCapacites {

    private DrapeauxCapacites() {
    }

`;
  for (const [nom, ids] of Object.entries(sets)) src += javaSet(nom, ids) + '\n';
  src += '    /** Recul : fraction des dégâts infligés {numérateur, dénominateur}. */\n';
  src += javaMap('RECUL', recul, e => `new int[]{${e[1]}, ${e[2]}}`) + '\n';
  src += '    /** Priorité non nulle (les capacités de statut absentes des données en sont exclues). */\n';
  src += javaMap('PRIORITE', priorite, e => `${e[1]}`);
  src += '}\n';
  const dest = path.join('/home/claude', mod, 'src/main/java/com/tropimon', mod, 'calc/DrapeauxCapacites.java');
  fs.writeFileSync(dest, src);
  console.log('écrit', dest);
}
console.log(Object.fromEntries(Object.entries(sets).map(([k, v]) => [k, v.length])), 'recul', recul.length, 'priorité', priorite.length);
