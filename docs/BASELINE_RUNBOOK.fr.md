# Procédure de référence M0.5

## 1. Enregistrer la provenance

Noter URL, commit immuable, construction, correctifs, empreintes binaires et
licences du client amont. Ne pas importer son code tant que l’ADR-0001 attend.

## 2. Capturer Windows

```powershell
./tools/powershell/collect-windows-baseline.ps1 -OutFile docs/evidence/windows-environment.json
```

## 3. Tester un client connu fonctionnel

Exécuter découverte, enregistrement, connexion, dix minutes de streaming, arrêt
propre et reconnexion. Conserver horodatages et métriques, jamais les secrets.

## 4. Inspecter le GP520

**Gate matériel différé :** ne pas exécuter cette section avant l'autorisation
explicite de connecter le GP520. Les résultats restent UNKNOWN jusque-là.

Après autorisation ADB sur le projecteur :

```powershell
./tools/powershell/collect-gp520-baseline.ps1 -OutFile docs/evidence/gp520-environment.json
```

`-OutFile` écrit en UTF-8 sans BOM. Ne pas rediriger vers `Set-Content -Encoding utf8` :
Windows PowerShell 5.1 ajoute un BOM que les lecteurs JSON stricts rejettent.

Relire le résultat. Un champ `dumpsys` absent signifie UNKNOWN.

## 5. Valider la DualSense

Tester chaque commande en USB puis Bluetooth et conserver codes, axes, plages,
zones mortes, transitions, version OS et firmware.

## 6. Promouvoir des fixtures

Après expurgation et revue :

```powershell
$env:PYTHONPATH = 'tools/python'
python -m gpsx_evidence.cli manifest tests/fixtures tests/fixtures/manifest.json
```

