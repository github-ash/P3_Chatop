# ChâTop - API Java

Back-end Spring Boot du portail de locations saisonnières.
Les neuf routes du contrat Mockoon sont implémentées sans modification du
front-end Angular.

## Stack et architecture

- Java 21 minimum ; compilation et tests réalisés avec le JDK 26.
- Spring Boot 4.0.8, Spring Security avec JWT et BCrypt.
- Spring Data JPA et MySQL 8.
- Springdoc OpenAPI 3.0.3 et Swagger UI.
- Maven Wrapper inclus ; H2 est utilisé uniquement pour les tests.

```text
Controller -> Service -> JPA Repository -> MySQL
```

Les entités ne sont jamais renvoyées directement au client. Les DTO définissent
le contrat HTTP. Les erreurs sont centralisées et les erreurs serveur inattendues
sont journalisées sans exposer leur détail au client.

Les contrôleurs, services, entités, DTO, repositories et configurations sont
présentés par un JavaDoc indiquant leur responsabilité. Les opérations métier et
les handlers HTTP principaux précisent leurs paramètres et leurs réponses.
Les annotations OpenAPI décrivent en plus les routes dans Swagger UI.

## Base de données

Créer `chatop_db`, puis importer le schéma officiel du starter Angular :
`..\P3_chatop\ressources\sql\script.sql`.

Ne pas réimporter le script si les tables existent déjà.
Les tables sont `USERS`, `RENTALS`, `MESSAGES`, avec un index unique sur
l'email et trois clés étrangères. Hibernate utilise `ddl-auto=validate` :
il vérifie le schéma mais ne le modifie pas.

Le schéma fourni utilise `NUMERIC` sans décimales, équivalent à
`DECIMAL(10,0)` sous MySQL. L'API accepte donc des nombres entiers pour le
prix et la surface, au maximum dix chiffres. Les montants Java utilisent
`BigDecimal`, sans arrondi silencieux. Une évolution vers les centimes
nécessite une migration validée du schéma.

Utiliser un compte applicatif local limité à `SELECT`, `INSERT`, `UPDATE`
sur `chatop_db`, jamais `root`. Le compte local vérifié possède aussi `DELETE`,
mais aucun privilège de création de table ou d'administration.

## Configuration locale

Définir ces variables dans l'environnement de lancement ou dans un fichier
`.env` à la racine du back-end :

```dotenv
CHATOP_DB_URL=jdbc:mysql://localhost:3308/chatop_db
CHATOP_DB_USERNAME=<compte MySQL limite>
CHATOP_DB_PASSWORD=<mot de passe de ce compte>
CHATOP_JWT_SECRET=<au moins 32 octets aleatoires encodes en Base64>
```

Les valeurs entre chevrons sont à remplacer localement, jamais à versionner.
Le port MySQL de ce poste est **3308** ; l'adapter pour une autre installation.
Le fichier `.env` est exclu de Git. Il n'est pas chiffré : restreindre son accès
sur le poste et ne jamais le partager. Ne pas mettre ces secrets dans Angular.
Si un secret a déjà été partagé ou versionné, le renouveler.

Le script `start.ps1` charge uniquement les lignes `CHATOP_NOM=valeur`, sans
exécuter leur contenu et sans afficher les valeurs. Une variable déjà définie
dans le processus est prioritaire. Les valeurs doivent tenir sur une ligne,
sans guillemets ajoutés autour de la valeur. Le script ne développe pas `${...}`.
Spring Boot ne lit pas `.env` tout seul.

Pour générer une clé JWT, dans PowerShell :

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$env:CHATOP_JWT_SECRET = [Convert]::ToBase64String($bytes)
$rng.Dispose()
Remove-Variable bytes, rng
```

Cette commande définit la clé uniquement pour le terminal courant.
Conserver la même clé pour que les tokens restent valides entre redémarrages.
Ne pas utiliser une clé d'exemple publique.

Variables facultatives :

| Variable | Défaut | Rôle |
|---|---|---|
| `CHATOP_SERVER_PORT` | `3001` | Port de l'API |
| `CHATOP_IMAGES_DIRECTORY` | `uploads` | Répertoire persistant des images |
| `CHATOP_IMAGES_BASE_URL` | `http://localhost:3001/images` | URL des images accessible au navigateur |

Sur un serveur, utiliser HTTPS et une URL d'images correspondant au domaine
réel. Sauvegarder ensemble la base et le répertoire des images.

## Lancement Windows

Arrêter Mockoon : il utilise le même port 3001 que le back-end.

Le chemin long de ce poste contient un caractère de saut de ligne qui perturbe
le processus Java lancé par Maven. Utiliser le lecteur court existant :

```powershell
Set-Location 'Q:\chatop-backend'
.\start.ps1
```

Si `Q:` n'existe plus, depuis le dossier parent contenant `P3_chatop` et
`chatop-backend`, et uniquement si la lettre Q: est libre :

```powershell
subst Q: "$((Get-Location).Path)"
Set-Location 'Q:\chatop-backend'
.\start.ps1
```

`Q:` est un alias du dossier, pas une copie.
Si les variables sont déjà définies, la commande Maven directe fonctionne aussi :

```powershell
.\mvnw.cmd spring-boot:run
```

API : http://localhost:3001

Pour lancer le front existant dans un second terminal :

```powershell
Set-Location 'Q:\P3_chatop'
npm run start -- --host localhost --port 4200 --prebundle=false --proxy-config src\proxy.config.json
```

Ouvrir http://localhost:4200/register et créer un compte. Les identifiants
fictifs Mockoon ne sont pas automatiquement créés dans MySQL.

## Routes

| Méthode | URL | Entrée | Succès |
|---|---|---|---|
| POST | `/api/auth/register` | JSON : name, email, password | 200, token |
| POST | `/api/auth/login` | JSON : email, password | 200, token |
| GET | `/api/auth/me` | JWT | 200, profil |
| GET | `/api/rentals` | JWT | 200, `{ "rentals": [...] }` |
| GET | `/api/rentals/{id}` | JWT et identifiant | 200, annonce |
| POST | `/api/rentals` | JWT et multipart : name, surface, price, description, picture | 200, `Rental created !` |
| PUT | `/api/rentals/{id}` | JWT et multipart : name, surface, price, description | 200, `Rental updated !` |
| GET | `/api/user/{id}` | JWT et identifiant | 200, profil sans password |
| POST | `/api/messages` | JWT et JSON : rental_id, user_id, message | 200, `Message send with success` |

Les confirmations sont des objets JSON avec une propriété `message`.
Les créations conservent le statut 200 du contrat fourni.
Aucune suppression n'est définie dans Mockoon : aucun DELETE n'est inventé.

Erreurs : 400 pour requête invalide ou email déjà utilisé ; 401 pour
authentification absente/invalide/expirée ; 403 pour opération interdite ;
404 pour ressource absente ; 413 pour upload trop volumineux au niveau HTTP ;
415 pour type de requête non supporté ; 500 pour erreur serveur inattendue.
Les erreurs 400 renvoient `{}` ; la connexion refusée renvoie
`{"message":"error"}`. Les détails internes ne sont pas renvoyés au client.

## Sécurité et images

- Seuls register, login et Swagger sont publics.
- Les JWT utilisateur sont signés HS256, valables une heure et vérifiés
  pour leur signature, leur expiration et l'émetteur `chatop`.
- Les mots de passe sont hachés BCrypt. À l'inscription : au moins 8 caractères,
  au plus 72 octets UTF-8. Les règles serveur sont plus strictes que celles du
  formulaire existant ; une valeur refusée donne un 400.
- Seul le propriétaire peut modifier une annonce.
- L'expéditeur d'un message doit correspondre au JWT.
- `picture` est une chaîne dans toutes les réponses, conformément à l'interface
  Angular et au schéma SQL, malgré le tableau présent dans un exemple Mockoon.
- Une image PNG ou JPEG est obligatoire à la création, maximum 5 Mo et
  16 mégapixels. Le contenu réel est vérifié et réencodé en PNG.
- Le nom d'origine n'est jamais utilisé comme chemin : le fichier reçoit un UUID.
- La base conserve l'URL stable, pas le fichier ni le jeton d'accès.
- Une annulation de transaction supprime le fichier nouvellement enregistré.
- La modification conserve l'image et le propriétaire existants.

Une balise HTML `img` n'ajoute pas l'en-tête JWT de l'intercepteur Angular.
Les réponses d'annonces fournissent donc une URL munie d'un **JWT temporaire
limité à une seule image**, valable une heure. Ce jeton ne permet aucun appel
métier ni accès à une autre image. Sans JWT valide, l'image reste inaccessible.
Le JWT utilisateur en en-tête permet également de lire une image.
Les jetons dans les paramètres URL ne sont acceptés que pour les GET d'images.
Le serveur ne les journalise pas dans un access log ; ne pas activer une
journalisation des URL complètes sur un proxy. Ne pas partager ces URL.
Après expiration de l'image, recharger l'annonce pour obtenir une nouvelle URL.

## Swagger

- Interface : http://localhost:3001/swagger-ui/index.html
- Document OpenAPI : http://localhost:3001/v3/api-docs

Appeler register ou login, copier le token, cliquer sur **Authorize**, puis
coller le token pour essayer les routes protégées.
L'accès public à la documentation n'ouvre pas l'accès public aux données.

## Tests et résultats

```powershell
.\mvnw.cmd -B -ntp verify
```

19 tests d'intégration vérifient les neuf routes, BCrypt, les JWT, les DTO,
la validation, la persistance H2, les droits, les images et Swagger.
Les tests utilisent des bases H2 isolées et des clés JWT aléatoires.
Le JAR est produit dans `target`.

Une recette réelle a aussi été exécutée sur MySQL 8.0.46, port 3308 :
inscription et connexion depuis Angular, profil, liste, propriétaire, création avec image
visible, détail, envoi d'un message et modification du prix.
La persistance du message, du prix et du hash BCrypt a été vérifiée dans MySQL.
Des données identifiées « Recette » ont été conservées pour permettre leur
inspection ; ce ne sont pas des comptes de production.

## Organisation et livraison

- `auth` : inscription, connexion et profil courant.
- `user` : entité, repository, service et DTO utilisateur.
- `rental` : annonces, droits de propriété et DTO multipart.
- `message` : enregistrement des messages et contrôle d'expéditeur.
- `image` : stockage et lecture des images authentifiées.
- `security` : JWT et BCrypt.
- `config` : OpenAPI.
- `error` : gestion des erreurs HTTP.

Le bilan détaillé est dans [BILAN_DEVELOPPEMENT.txt](./BILAN_DEVELOPPEMENT.txt).
La publication GitHub et la validation du mentor restent des actions de livraison
à réaliser avec le dépôt et les accès choisis par le propriétaire du projet.
Le dépôt local est initialisé sur `main`, avec une branche `release/1.0.0`.
Le fichier `.env`, les images et les résultats de compilation sont exclus du commit.
