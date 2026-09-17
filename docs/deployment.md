# Deployment (backend → Cloud Run + Cloud SQL)

None of this has been run against a real GCP project by whoever/whatever wrote this doc — these are the exact commands to run, not a report of a completed deployment. Follow [cloud-services.md](cloud-services.md) first for account/API setup context.

## 1. Create the Cloud SQL instance

```bash
gcloud sql instances create pinghome-db \
  --database-version=MYSQL_8_0 \
  --tier=db-f1-micro \
  --region=us-central1 \
  --root-password=<choose-a-strong-password>

gcloud sql databases create pinghome --instance=pinghome-db

gcloud sql users create pinghome \
  --instance=pinghome-db \
  --password=<choose-a-strong-password>
```

Note the instance connection name (`gcloud sql instances describe pinghome-db --format='value(connectionName)'`) — you'll need it for Cloud Run's Cloud SQL connector.

## 2. Build and push the backend image

```bash
cd backend
gcloud builds submit --tag gcr.io/<PROJECT_ID>/pinghome-backend
```

(Or build locally with `docker build -t gcr.io/<PROJECT_ID>/pinghome-backend . && docker push ...` if you'd rather not use Cloud Build.)

## 3. Deploy to Cloud Run

```bash
gcloud run deploy pinghome-backend \
  --image gcr.io/<PROJECT_ID>/pinghome-backend \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated \
  --min-instances=0 \
  --max-instances=3 \
  --add-cloudsql-instances=<PROJECT_ID>:us-central1:pinghome-db \
  --set-env-vars="SPRING_PROFILES_ACTIVE=cloud" \
  --set-env-vars="DB_URL=jdbc:mysql:///pinghome?cloudSqlInstance=<PROJECT_ID>:us-central1:pinghome-db&socketFactory=com.google.cloud.sql.mysql.SocketFactory&useSSL=false" \
  --set-env-vars="DB_USERNAME=pinghome" \
  --set-secrets="DB_PASSWORD=pinghome-db-password:latest" \
  --set-secrets="PINGHOME_JWT_SECRET=pinghome-jwt-secret:latest" \
  --set-secrets="PINGHOME_FIREBASE_CREDENTIALS_JSON=pinghome-firebase-creds:latest"
```

`--allow-unauthenticated` is required here: the JWT layer is PingHome's own auth, and Cloud Run's separate IAM-based auth would block the Android app's requests before they ever reach Spring Security. Application-level auth is what actually protects the API.

The Cloud SQL JDBC URL above uses the [Cloud SQL Socket Factory](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory), which needs the `com.google.cloud.sql:mysql-socket-factory-connector-j-8` dependency added to `pom.xml` if you deploy this way instead of a public IP + SSL connection — pick whichever connection method matches your Cloud SQL instance's configuration and adjust `DB_URL` accordingly.

**Secrets**: store `DB_PASSWORD`, `PINGHOME_JWT_SECRET`, and the Firebase service-account JSON in [Secret Manager](https://cloud.google.com/secret-manager) rather than as plain `--set-env-vars` — the command above already does this via `--set-secrets`. Create them first:

```bash
echo -n "<db-password>" | gcloud secrets create pinghome-db-password --data-file=-
openssl rand -base64 48 | gcloud secrets create pinghome-jwt-secret --data-file=-
gcloud secrets create pinghome-firebase-creds --data-file=path/to/service-account.json
```

## 4. Point the Android app at it

Set `PINGHOME_API_BASE_URL` (the release build config field in `android/app/build.gradle`) to the Cloud Run service URL printed after deploy (`https://pinghome-backend-xxxxx-uc.a.run.app/`).

## 5. Tear down after grading/demo, to stop all billing

```bash
gcloud run services delete pinghome-backend --region us-central1
gcloud sql instances delete pinghome-db
gcloud secrets delete pinghome-db-password
gcloud secrets delete pinghome-jwt-secret
gcloud secrets delete pinghome-firebase-creds
```

See [cloud-services.md](cloud-services.md) for the per-service cost/free-tier breakdown and lighter-weight "pause instead of delete" options if you'll need the environment again soon.
