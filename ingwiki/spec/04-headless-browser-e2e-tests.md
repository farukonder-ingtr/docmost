# E2E Test Planı — Headless Browser + Canlı Ortam

> [03-final-architecture-and-use-cases.md](./03-final-architecture-and-use-cases.md)'teki mimariyi doğrulamak için tekrarlanabilir test script'i. Her kod değişikliğinden sonra bu adımlar yeniden çalıştırılmalıdır.

## 1. Ön Koşullar

```bash
cd /home/onder/dev/docmost/ingwiki
test -f .env || cp .env.example .env   # ilk kurulumda secret'ları doldurun
docker compose up -d
docker compose ps   # hepsi Up/healthy olmalı: nginx, docmost, authz, docmost-postgres, redis, ldap
```

Test LDAP dizini: `dc=placeholder,dc=test`, kullanıcılar `faruk/ali/ayse` (parolalar `faruk123`/`ali123`/`ayse123`), grup `INGWIKI_SPACE1` (üye: `ali`).

## 2. Headless Browser İçin Geçici HTTP Shim

Headless browser self-signed sertifikaya güvenmediği için, yalnızca tarayıcı testi süresince geçici bir düz-HTTP shim kullanılır:

```bash
cat > docker-compose.override.yml <<'EOF'
services:
  docmost:
    environment:
      APP_URL: http://ingwiki:3000
    ports:
      - "3000:3000"
    networks:
      - backend
      - frontend
EOF
docker compose -f docker-compose.yml -f docker-compose.override.yml up -d --force-recreate docmost

# ... testler ...

# geri al (TLS/nginx kalıcı durumdur)
rm -f docker-compose.override.yml
docker compose up -d --force-recreate docmost
docker compose restart nginx   # force-recreate sonrası nginx'in eski IP'yi cache'lemesi ihtimaline karşı
```

## 3. API Testleri (curl, TLS üzerinden)

```bash
curl -sk -o /dev/null -w "HTTP %{http_code}\n" https://ingwiki/
curl -sk https://ingwiki/api/health   # {"status":"ok", database: up, redis: up}

# LDAP giriş — başarı
curl -sk -X POST https://ingwiki/api/auth/ldap-login -H 'Content-Type: application/json' \
  -d '{"username":"ali","password":"ali123"}' -o /dev/null -w 'HTTP %{http_code}\n'   # 200

# LDAP giriş — hatalı parola / bilinmeyen kullanıcı (aynı hata)
curl -sk -X POST https://ingwiki/api/auth/ldap-login -H 'Content-Type: application/json' \
  -d '{"username":"ali","password":"wrong"}' -o /dev/null -w 'HTTP %{http_code}\n'    # 401
curl -sk -X POST https://ingwiki/api/auth/ldap-login -H 'Content-Type: application/json' \
  -d '{"username":"nobody","password":"x"}' -o /dev/null -w 'HTTP %{http_code}\n'     # 401

# authz içsel endpoint'i secret olmadan reddedilir
docker compose exec docmost sh -c "wget -qO- --post-data='{}' http://authz:8080/internal/authenticate" # 403
```

## 4. Headless Browser Testleri

### 4.1 Tek Form / Tek Buton Login

1. `http://ingwiki:3000/login` adresini aç. **Beklenen**: Tek bir "Email or username" alanı, bir "Password" alanı, tek bir "Sign In" butonu (ayrı bir LDAP butonu yok).
2. **Email or username**'a `ali` (e-posta formatında olmayan düz LDAP kullanıcı adı), **Password**'e `ali123` yaz, "Sign In"e tıkla. **Beklenen**: Hiç inline format hatası çıkmadan `/home`'a yönlendirilir (LDAP denemesi arka planda otomatik başarılı oldu).
3. Çıkış yap. Bir yerel Docmost hesabıyla (LDAP'ta karşılığı yok) yanlış parola dene. **Beklenen**: Ağ sekmesinde önce `/api/auth/ldap-login`→`401`, sonra `/api/auth/login`→`401`; ekranda yalnızca genel **"Login failed"** alert'i.
4. Çıkış yap. E-posta formatında olmayan rastgele bir kullanıcı adı + rastgele parola dene. **Beklenen**: `/api/auth/ldap-login`→`401`, `/api/auth/login`→`400` (e-posta format hatası); yine aynı genel **"Login failed"** alert'i — `400`/`401` farkı kullanıcıya hiç sızmaz.

### 4.2 LDAP Kullanıcı Provisioning

1. `docker compose exec docmost-postgres psql -U docmost -d docmost -c "SELECT email, auth_source, password IS NULL AS pw_null FROM users WHERE email='ali@placeholder.test';"` çalıştır. **Beklenen**: `auth_source='ldap'`, `pw_null=t`.
2. `ali@placeholder.test` + herhangi bir parola ile yerel girişi (`/api/auth/login`) dene. **Beklenen**: Temiz `401`, `docker compose logs docmost | grep -i error` boş döner (sunucu çökmez).

### 4.3 LDAP Grubuna Göre Otomatik Space Üyeliği

Ön koşul: workspace'te `INGWIKI-SPACE1` adlı bir space mevcut olmalı (yoksa workspace admin'i UI'dan "New space" ile oluşturur).

1. `ali`/`ali123` ile LDAP girişi yap. **Beklenen**: `/home`'a yönlendirilir.
2. `docker compose exec docmost-postgres psql -U docmost -d docmost -c "SELECT s.name, sm.role FROM space_members sm JOIN users u ON u.id=sm.user_id JOIN spaces s ON s.id=sm.space_id WHERE u.email='ali@placeholder.test';"` çalıştır. **Beklenen**: `INGWIKI-SPACE1` satırı, rol `writer`; workspace'te `INGWIKI-SPACE2` gibi başka bir space varsa onda üyelik **yoktur**.
3. `/spaces` sayfasına git. **Beklenen**: `ali` yalnızca üye olduğu space'leri görür.
4. Üye olduğu space'e tıkla. **Beklenen**: "New page"/"Create page"/"Space settings" butonları görünür (native `writer` yetkisi).
5. Üye olmadığı bir space'e doğrudan URL ile git. **Beklenen**: Temiz bir `404`.

### 4.4 `authz`'nin Veritabanısız Çalıştığının Doğrulanması

1. `docker compose logs authz --tail=30` çalıştır. **Beklenen**: Loglarda `datasource`/`JPA`/`Hibernate` ile ilgili hiçbir satır yok; yalnızca `LDAP repositories`, `Redis repositories`, `Tomcat started`, `Started DocmostAuthzApplication`.
2. `docker compose ps` çalıştır. **Beklenen**: `authz-postgres` adlı bir servis/container **yoktur**.

## 5. Birim Testleri (`authz`)

```bash
cd ingwiki/authz && mvn clean test
```
**Beklenen**: 13/13 test geçer (`LdapServiceTest`, `LdapAuthenticationControllerTest`, `InternalSecretFilterTest`).

## 6. Son Doğrulama Durumu

**2026-10-05** tarihinde yukarıdaki tüm senaryolar canlı ortamda (headless browser + curl + `mvn clean test`) çalıştırıldı ve geçti. Stack: 6 servis (nginx, docmost, docmost-postgres, authz, redis, ldap) healthy, `authz`'de veritabanı yok, `https://ingwiki/` → `200`.

---
Doküman serisi: [01-docmost-oss-architecture.md](./01-docmost-oss-architecture.md) → [02-requirements-1.md](./02-requirements-1.md) → [03-final-architecture-and-use-cases.md](./03-final-architecture-and-use-cases.md) → 04 (bu doküman).
