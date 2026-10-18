# Instructions de signature Release — Wali Songhaï

Ce document explique comment générer la clé de signature pour la publication sur le Google Play Store et comment construire le fichier Android App Bundle (`.aab`).

---

## 1. Génération du Keystore avec `keytool`

Exécutez la commande suivante dans votre terminal (à la racine du projet ou dans un répertoire sécurisé) :

```bash
keytool -genkeypair -v \
  -keystore my-upload-key.jks \
  -alias upload \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

> **Remarque :**  
> - `keytool` vous demandera de choisir un mot de passe pour le keystore (`STORE_PASSWORD`) et pour la clé (`KEY_PASSWORD`).  
> - Choisissez des mots de passe robustes et **conservez-les précieusement** dans un gestionnaire de mots de passe.  
> - Ne partagez ni ne committez jamais le fichier `.jks` sur un dépôt public (il est déjà exclu par `.gitignore`).

---

## 2. Emplacement attendu

Placez le fichier généré `my-upload-key.jks` à la racine du projet :
```text
/my-upload-key.jks
```
*(Ou définissez la variable `KEYSTORE_PATH` vers l'emplacement de votre fichier si vous préférez le conserver en dehors du projet).*

---

## 3. Fourniture des mots de passe

Vous pouvez transmettre les mots de passe de deux manières sécurisées sans jamais les inscrire dans le code :

### Option A — Variables d'environnement (recommandé pour CI/CD ou terminal local) :
```bash
export STORE_PASSWORD="<votre_mot_de_passe_keystore>"
export KEY_PASSWORD="<votre_mot_de_passe_cle>"
```

### Option B — Propriétés Gradle en ligne de commande :
```bash
gradle :app:bundleRelease -PSTORE_PASSWORD="<votre_mot_de_passe_keystore>" -PKEY_PASSWORD="<votre_mot_de_passe_cle>"
```

---

## 4. Commande de génération du bundle AAB

Une fois le keystore en place et les mots de passe renseignés :

```bash
gradle :app:bundleRelease
```

Le fichier signé sera généré dans :
```text
app/build/outputs/bundle/release/app-release.aab
```
Ce fichier `.aab` est prêt à être téléversé directement sur la **Google Play Console**.
