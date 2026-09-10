import os

# Traduções para os idiomas mais importantes
translations = {
    "es_ES.yml": {
        "lang_set": "&a[FormulaRacing] Tu idioma fue actualizado a: ",
        "welcome_first_time": "&ase unió al servidor por primera vez!",
        "no_permission": "&cNo tienes permiso para usar este comando.",
        "player_not_found": "&cJugador no encontrado.",
        "command_only_players": "&cEste comando solo puede ser ejecutado por jugadores.",
        "invalid_number": "&cNúmero inválido.",
        "wait_before_click": "&cEspera medio segundo antes de hacer clic de nuevo!",
        "timetrial_teleport": "&eTeletransportado a [&f{track}&e]",
        "timetrial_menu_title": "&aElige una pista",
        "timetrial_completed": "&6🏁 &fTiempo: &b{time}",
        "track_not_found": "&cPista no encontrada con ese nombre.",
        "track_is_closed": "&c¡Esta pista está cerrada!",
        "timetrial_cancelled": "&cTiempo de prueba cancelado.",
        "race_start": "&a¡Carrera iniciada!",
        "race_finish": "&6§lCarrera finalizada!",
        "race_win": "&6§l§k§r &6§lVICTORIA! &6§l§k§r &a¡Felicidades, ganaste!",
        "ready_check_title": "&e§lREADY CHECK",
        "ready_check_press": "&7Presiona &eSHIFT &7para estar listo!",
        "ready_check_ready": "&a¡Estás listo!",
        "ready_check_all_ready": "&a§l¡Todos los pilotos están listos!",
        "heat_start": "&aHeat {heat} iniciado!",
        "heat_finish": "&aHeat {heat} finalizado!",
        "checkpoint_pass": "&a¡Checkpoint {current}/{total}!",
        "error_no_heat": "&cNo estás en un heat activo.",
        "party_created": "&a¡Party creada!",
        "party_disbanded": "&cParty eliminada.",
        "party_joined": "&a¡Te uniste a la party!",
        "party_left": "&cSaliste de la party.",
        "event_created": "&a&l✔ &aEvento creado: &e{event}",
        "event_started": "&a&l✔ &aEvento iniciado!",
        "event_finished": "&a&l✔ &aEvento finalizado!",
        "reload_success": "&a[FormulaRacing] Plugin recargado!",
    },
    "de_DE.yml": {
        "lang_set": "&a[FormulaRacing] Sprache geändert auf: ",
        "welcome_first_time": "&aist dem Server zum ersten Mal beigetreten!",
        "no_permission": "&cDu hast keine Berechtigung für diesen Befehl.",
        "player_not_found": "&cSpieler nicht gefunden.",
        "command_only_players": "&cDieser Befehl kann nur von Spielern ausgeführt werden.",
        "invalid_number": "&cUngültige Nummer.",
        "wait_before_click": "&cWarte eine halbe Sekunde bevor du erneut klickst!",
        "timetrial_teleport": "&eTeleportiert zu [&f{track}&e]",
        "timetrial_menu_title": "&aWähle eine Strecke",
        "timetrial_completed": "&6🏁 &fZeit: &b{time}",
        "track_not_found": "&cStrecke mit diesem Namen nicht gefunden.",
        "track_is_closed": "&cDiese Strecke ist geschlossen!",
        "timetrial_cancelled": "&cZeitlauf abgebrochen.",
        "race_start": "&aRennen gestartet!",
        "race_finish": "&6§lRennen beendet!",
        "race_win": "&6§l§k§r &6§lSIEG! &6§l§k§r &aHerzlichen Glückwunsch, du hast gewonnen!",
        "ready_check_title": "&e§lREADY CHECK",
        "ready_check_press": "&7Drücke &eSHIFT &7um bereit zu sein!",
        "ready_check_ready": "&aDu bist bereit!",
        "ready_check_all_ready": "&a§lAlle Piloten sind bereit!",
        "heat_start": "&aHeat {heat} gestartet!",
        "heat_finish": "&aHeat {heat} beendet!",
        "checkpoint_pass": "&aCheckpoint {current}/{total}!",
        "error_no_heat": "&cDu bist nicht in einem aktiven Heat.",
        "party_created": "&aGruppe erstellt!",
        "party_disbanded": "&cGruppe aufgelöst.",
        "party_joined": "&aDu bist der Gruppe beigetreten!",
        "party_left": "&cDu hast die Gruppe verlassen.",
        "reload_success": "&a[FormulaRacing] Plugin neu geladen!",
    },
    "fr_FR.yml": {
        "lang_set": "&a[FormulaRacing] Langue changée en: ",
        "welcome_first_time": "&a rejoint le serveur pour la première fois!",
        "no_permission": "&cVous n'avez pas la permission pour cette commande.",
        "player_not_found": "&cJoueur non trouvé.",
        "command_only_players": "&cCette commande ne peut être exécutée que par des joueurs.",
        "invalid_number": "&cNombre invalide.",
        "wait_before_click": "&cAttendez une demi-seconde avant de cliquer!",
        "timetrial_teleport": "&eTéléporté à [&f{track}&e]",
        "timetrial_menu_title": "&aChoisissez une piste",
        "timetrial_completed": "&6🏁 &fTemps: &b{time}",
        "track_not_found": "&cPiste non trouvée avec ce nom.",
        "track_is_closed": "&cCette piste est fermée!",
        "timetrial_cancelled": "&cEssai annulé.",
        "race_start": "&aCourse commencée!",
        "race_finish": "&6§lCourse terminée!",
        "race_win": "&6§l§k§r &6§lVICTOIRE! &6§l§k§r &aFélicitations, vous avez gagné!",
        "ready_check_title": "&e§lREADY CHECK",
        "ready_check_press": "&7Appuyez sur &eSHIFT &7pour être prêt!",
        "ready_check_ready": "&aVous êtes prêt!",
        "ready_check_all_ready": "&a§lTous les pilotes sont prêts!",
        "heat_start": "&aHeat {heat} commencé!",
        "heat_finish": "&aHeat {heat} terminé!",
        "checkpoint_pass": "&aCheckpoint {current}/{total}!",
        "error_no_heat": "&cVous n'êtes pas dans un heat actif.",
        "party_created": "&aGroupe créé!",
        "party_disbanded": "&cGroupe supprimé.",
        "party_joined": "&aVous avez rejoint le groupe!",
        "party_left": "&cVous avez quitté le groupe.",
        "reload_success": "&a[FormulaRacing] Plugin rechargé!",
    },
}

# Aplica traduções
for filename, trans in translations.items():
    if os.path.exists(filename):
        with open(filename, 'r', encoding='utf-8') as f:
            content = f.read()
        
        for key, value in trans.items():
            # Substitui a linha se existir
            lines = content.split('\n')
            for i, line in enumerate(lines):
                if line.startswith(key + ':'):
                    lines[i] = f'{key}: "{value}"'
                    break
            content = '\n'.join(lines)
        
        with open(filename, 'w', encoding='utf-8') as f:
            f.write(content)
        
        print(f"Traduzidas {len(trans)} chaves em {filename}")

print("Concluído!")
