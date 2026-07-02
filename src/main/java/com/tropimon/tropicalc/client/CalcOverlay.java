List<MoveTemplate> coupsAdv = ObservationCollector.getCoupsAdversaireReveles(especeAdv);
        if (!coupsAdv.isEmpty()) {
            y += 4;
            context.drawText(client.textRenderer, Text.literal("Attaques adverses :"), x, y, COULEUR_DANGER, true);
            y += hauteurLigne;

            for (MoveTemplate template : coupsAdv) {
                com.tropimon.tropicalc.calc.Move capaciteAdv = convertirTemplate(template);
                String nom = template.getDisplayName().getString();
                String ligne;
                int couleur = COULEUR_TEXTE;

                if (capaciteAdv == null || capaciteAdv.estCapaciteDeStatut()) {
                    ligne = nom + " : statut";
                } else {
                    DamageCalculator.Resultat r = DamageCalculator.calculer(adversaire, joueur, capaciteAdv, field, null, false);
                    if (r.immunise) {
                        ligne = nom + " : immunisé";
                    } else {
                        ligne = String.format("%s : %.0f%% - %.0f%%", nom, r.pourcentageMin, r.pourcentageMax);
                        if (r.koGaranti) couleur = COULEUR_KO;
                        else if (r.koPossible) couleur = 0xFFAA00;
                    }
                }
                context.drawText(client.textRenderer, Text.literal(ligne), x, y, couleur, true);
                y += hauteurLigne;
            }
        }
