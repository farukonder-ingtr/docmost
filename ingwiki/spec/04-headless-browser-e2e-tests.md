# E2E Test Planı — Headless Browser + Canlı Ortam Doğrulaması

> Bu doküman, [03-final-architecture-and-use-cases.md](./03-final-architecture-and-use-cases.md) §5'te özetlenen canlı doğrulamanın **tekrarlanabilir, adım adım script'idir**. Amaç: her değişiklikten sonra aynı senaryoları çalıştırıp (manuel veya CI'da) sonuçları bu dosyadaki "Son Çalıştırma Sonuçları" tablosuna güncelleyerek sistemin hâlâ gereksinimleri karşıladığını kanıtlamak. Mimari detaylar için → [03-final-architecture-and-use-cases.md](./03-final-architecture-and-use-cases.md); gereksinimler için → [02-requirements-1.md](./02-requirements-1.md). Burada mimari **tekrar anlatılmaz**, yalnızca test adımları ve sonuçları yer alır.

## 1. Ön Koşullar

```bash
cd /home/onder/dev/docmost/ingwiki   # docker-compose.yml artık burada (bkz. 03, §11)
test -f .env || cp .env.example .env   # ilk kurulumda secret'ları doldurun
docker compose up -d
docker compose ps                       # hepsi Up/healthy olmalı
```

Testler iki şekilde çalıştırılır:

- **A) `curl` ile API testleri** — `https://ingwiki` (self-signed sertifika, `-k` ile) üzerinden, TLS/nginx dahil tam yoldan.
- **B) Headless browser testleri** — Chromium tabanlı araç self-signed sertifikaya güvenmediği için (`ERR_CERT_AUTHORITY_INVALID`), yalnızca bu adım için geçici bir **düz HTTP dev shim** kullanılır (bkz. §2).

## 2. Headless Browser İçin Geçici HTTP Shim

Nginx/TLS yolunu bozmadan, yalnızca tarayıcı testi süresince `docmost` servisini `ingwiki:3000` üzerinden düz HTTP ile de erişilebilir kılan bir `docker-compose.override.yml`:

```yaml
# Temporary, local-only: lets the headless browser verify the UI over plain HTTP
# (it refuses the self-signed nginx cert). Remove this file to go back to the
# TLS/nginx entrypoint, which is the intended production-like path.
services:
  docmost:
    environment:
      APP_URL: http://ingwiki:3000
    ports:
      - "3000:3000"
    networks:
      - backend
      - frontend   # backend ağı internal:true olduğundan port yayınlamak için frontend de gerekir (bkz. 03, §9 B7)
```

Uygulama ve geri alma:

```bash
# uygula (yalnızca tarayıcı testi süresince)
docker compose -f docker-compose.yml -f docker-compose.override.yml up -d --force-recreate docmost

# ... headless browser testleri ...

# geri al (TLS/nginx'e dön — kalıcı durum budur)
rm -f docker-compose.override.yml
docker compose up -d --force-recreate docmost
```

`docker-compose.override.yml` dosyasının `read_file`/`replace_string_in_file` gibi editör araçlarıyla düzenlenemediği (Copilot tarafından "ignored" işaretli) durumlarda terminalde `cat > docker-compose.override.yml <<'EOF' ... EOF` kullanılmalıdır.

## 3. Test Senaryoları (Script)

### 3.1 Altyapı Sağlık Kontrolü

```bash
curl -sk -o /dev/null -w "HTTP %{http_code}\n" https://ingwiki/
curl -sk https://ingwiki/api/health
```
**Beklenen:** İlki `200`; ikincisi `{"status":"ok",...,"database":{"status":"up"},"redis":{"status":"up"}}`.

### 3.2 LDAP ile Giriş (FR-1, FR-2, FR-3)

```bash
curl -sk -X POST https://ingwiki/api/auth/ldap-login \
  -H 'Content-Type: application/json' -c /tmp/faruk.txt \
  -d '{"username":"faruk","password":"faruk123"}' -o /dev/null -w 'HTTP %{http_code}\n'
curl -sk -X POST https://ingwiki/api/auth/ldap-login \
  -H 'Content-Type: application/json' -c /tmp/ali.txt \
  -d '{"username":"ali","password":"ali123"}' -o /dev/null -w 'HTTP %{http_code}\n'
curl -sk -X POST https://ingwiki/api/auth/ldap-login \
  -H 'Content-Type: application/json' -c /tmp/ayse.txt \
  -d '{"username":"ayse","password":"ayse123"}' -o /dev/null -w 'HTTP %{http_code}\n'
```
**Beklenen:** Üçü de `200` ve bir `authToken` çerezi döner (cookie dosyalarında görülebilir).

### 3.3 Olumsuz Senaryolar (FR-4)

```bash
curl -sk -X POST https://ingwiki/api/auth/ldap-login -H 'Content-Type: application/json' \
  -d '{"username":"faruk","password":"wrong"}' -o /dev/null -w 'HTTP %{http_code}\n'
curl -sk -X POST https://ingwiki/api/auth/ldap-login -H 'Content-Type: application/json' \
  -d '{"username":"nobody","password":"x"}' -o /dev/null -w 'HTTP %{http_code}\n'
```
**Beklenen:** İkisi de `401`, farklı bir hata mesajı/kod sızdırmaz (kullanıcı adı var/yok ayrımı yapılamaz).

### 3.4 Sayfa Yetkilendirme Matrisi (FR-5 — FR-10)

Daha önce oluşturulmuş `Finance Only Page` kaynağına `DOCMOST-FINANCE → VIEW` politikası tanımlıdır (bkz. 03, §5.3). `PAGE_ID` değeri ortam başına sabittir; her Docker volume sıfırlandığında (ör. compose proje adı değiştiğinde) sayfa+politika yeniden oluşturulmalı ve buradaki değer güncellenmelidir — en güncel değer: `01a106ba-f322-7e6f-9e83-3cdb8c4c6c39` (bkz. §4 son satır).

```bash
PAGE_ID=01a106ba-f322-7e6f-9e83-3cdb8c4c6c39

curl -sk -X POST https://ingwiki/api/pages/info -H 'Content-Type: application/json' \
  -b /tmp/faruk.txt -d "{\"pageId\":\"$PAGE_ID\"}" -o /dev/null -w 'faruk  -> HTTP %{http_code}\n'
curl -sk -X POST https://ingwiki/api/pages/info -H 'Content-Type: application/json' \
  -b /tmp/ali.txt -d "{\"pageId\":\"$PAGE_ID\"}" -o /dev/null -w 'ali    -> HTTP %{http_code}\n'
curl -sk -X POST https://ingwiki/api/pages/info -H 'Content-Type: application/json' \
  -b /tmp/ayse.txt -d "{\"pageId\":\"$PAGE_ID\"}" -o /dev/null -w 'ayse   -> HTTP %{http_code}\n'
```
**Beklenen:** `faruk` (DOCMOST-ADMIN bypass) → `200`; `ali` (DOCMOST-HR, eşleşmiyor) → `403`; `ayse` (DOCMOST-FINANCE, eşleşiyor) → `200`.

### 3.5 Headless Browser — Giriş ve Sayfa Erişimi (UI katmanı)

Headless browser aracı ile (bkz. §2'deki shim aktifken):

1. `http://ingwiki:3000/login` adresini aç.
2. Workspace'in yerel (LDAP dışı) admin hesabıyla giriş yap (`email`/`password` alanlarına yaz, "Sign In" tıkla).
3. `/home`'a yönlendiğini ve sayfanın gerçek Docmost arayüzünü render ettiğini doğrula (ekran görüntüsü).
4. Kenar çubuğundan `Finance Only Page`'e tıkla.
5. **Beklenen:** Sayfa içeriği yerine Docmost'un kendi "Page not found / you may not have access" ekranı görünür — çünkü bu hesabın LDAP karşılığı yoktur ve LDAP katmanı fail-closed çalışır (bkz. 03, §8 L4).

Bu adım, backend yetkilendirmesinin yalnızca API seviyesinde değil, **gerçek kullanıcı arayüzünde de** doğru şekilde yansıdığını kanıtlar (yanlışlıkla sayfa içeriğinin SPA cache'inden sızmadığını gösterir).

### 3.6 Headless Browser — Gerçek LDAP Girişi (web formu üzerinden, 03 §9 B8/B9)

Login formu **tek form + iki alt alta submit butonu**dur ("Sign In" ve "Sign in with company account (LDAP)"); aynı "Email or username" alanı her iki akış için paylaşılır:

1. `http://ingwiki:3000/login` adresini aç.
2. **Email or username** alanına `faruk`, **Password** alanına `faruk123` yaz.
3. "Sign in with company account (LDAP)" butonuna tıkla (alttaki, outline buton — üstteki "Sign In" değil).
4. **Beklenen:** `/home`'a yönlendirilir (bu buton `/api/auth/ldap-login`'e gider, `/api/auth/login`'e değil).
5. Kenar çubuğundan `Finance Only Page`'e tıkla.
6. **Beklenen:** `faruk` `DOCMOST-ADMIN` LDAP grubunda olduğu için sayfa içeriği (başlık + düzenlenebilir editor) görüntülenir — §3.5'teki yerel admin senaryosunun **tam tersi** bir sonuç.
7. Çıkış yap, aynı alana `faruk` + yanlış bir parola yazıp yine LDAP butonuna tıkla. **Beklenen:** Ekranda kırmızı bir alert ile `"Invalid LDAP credentials"` mesajı görünür (hata backend'den doğru şekilde dışarı aktarılıyor).
8. Aynı alana `faruk` (e-posta formatında değil) yazıp bu kez **üstteki** "Sign In" (şifre) butonuna tıkla. **Beklenen:** Hiç API çağrısı yapılmadan, alanın altında inline `"Enter a valid email"` hatası görünür — çünkü bu buton normal e-posta/parola girişini tetikler ve alan e-posta formatında değil.
9. Aynı alana `admin@placeholder.test`/`Password123!` yazıp "Sign In" butonuna tıkla. **Beklenen:** `/home`'a yönlendirilir (normal şifre girişinde regresyon yok).

### 3.7 Headless Browser — LDAP Kullanıcılarının Parolasız Provizyonu (03 §9 B10)

LDAP'tan provision edilen kullanıcıların Docmost `users` tablosunda artık gerçek bir parola olmadığını (`auth_source='ldap'`, `password=NULL`) ve bu kullanıcıların normal şifre formuyla **giriş yapamadığını** (sunucu tarafında çökme olmadan, temiz bir hata ile) doğrular:

1. `docker compose exec docmost-postgres psql -U docmost -d docmost -c "SELECT email, auth_source, password IS NULL AS password_is_null FROM users;"` ile mevcut LDAP kullanıcılarının (`faruk`/`ali`/`ayse`) `auth_source='ldap'` ve `password_is_null=t` olduğunu doğrula.
2. `http://ingwiki:3000/login` adresini aç, **Email or username** alanına `faruk` (LDAP kullanıcı adı), **Password** alanına `faruk123` yaz, LDAP butonuna tıkla. **Beklenen:** `/home`'a yönlendirilir (regresyon yok — oturum yolu zaten normal girişle aynı `sessionService.createSessionAndToken()` çağrısını kullanıyor).
3. Çıkış yap. **Email or username** alanına bu kez `faruk@placeholder.test`, **Password** alanına rastgele bir değer (`anything123`) yaz, **üstteki** "Sign In" (şifre) butonuna tıkla. **Beklenen:** Ekranda kırmızı bir alert ile `"Email or password does not match"` görünür — `401`, sunucu tarafında hiçbir hata/istisna (`docker compose logs docmost` içinde `error`/`exception` araması boş döner), çünkü `AuthService.login()` `bcrypt.compare`'i hiç çağırmadan `user.password === null` kontrolünde erken çıkar.

### 3.8 Headless Browser — LDAP Grubuna Göre Otomatik Space Üyeliği (03 §9 B11)

LDAP grubu `INGWIKI_<AD>` → Docmost space'i `INGWIKI-<AD>` eşleşmesinin, giriş anında otomatik üyelik oluşturduğunu ve bundan sonra native `SpaceRole` yetkilerinin geçerli olduğunu doğrular:

1. Test LDAP dizinine `cn=INGWIKI_SPACE1,ou=Groups,...` grubunu `ali` üyesiyle ekle (`ingwiki/test/ldap/bootstrap/test.ldif`'e eklenip `docker compose up ldap-seed --force-recreate` ile canlı dizine yükletildi).
2. Workspace'te `INGWIKI-SPACE1` ve `INGWIKI-SPACE2` adlı iki space oluştur (bu çalıştırmada admin şifresi elde olmadığı için doğrudan `INSERT INTO spaces (...)` ile; normalde workspace admin'i UI'dan "New space" ile oluşturur).
3. `http://ingwiki:3000/login` adresinde `ali`/`ali123` ile LDAP butonuna tıkla. **Beklenen:** `/home`'a yönlendirilir.
4. `docker compose exec docmost-postgres psql ... -c "SELECT u.email, s.name, sm.role FROM space_members sm JOIN users u ON ... JOIN spaces s ON ... WHERE u.email='ali@placeholder.test';"` çalıştır. **Beklenen:** Yalnızca `INGWIKI-SPACE1` satırı döner, rol `writer`; `INGWIKI-SPACE2` **yoktur**.
5. `/spaces` sayfasına git. **Beklenen:** Listede yalnızca `General` ve `INGWIKI-SPACE1` görünür (`INGWIKI-SPACE2` listelenmez).
6. `INGWIKI-SPACE1`'e tıkla. **Beklenen:** "New page", "Create page", "Space settings" butonları görünür — native `writer` yetkisinin geçerli olduğunu kanıtlar.
7. Doğrudan `http://ingwiki:3000/s/ingwiki-space2` adresine git. **Beklenen:** Temiz bir `404` (sunucu loglarında istisna yok) — üye olunmayan space'e erişim native olarak reddedilir.

### 3.9 Headless Browser — `authz`'nin DB'siz çalıştığının ve LDAP girişinin hâlâ çalıştığının doğrulanması (03 §9 B12)

`authz`'nin veritabanı olmadan başladığını ve DOCMOST-ADMIN/politika mekanizması kaldırıldıktan sonra LDAP kimlik doğrulamasının (ve B11'in) hâlâ çalıştığını doğrular:

1. `authz` imajını (JPA/Postgres bağımlılıkları kaldırılmış haliyle) yeniden build et, `authz-postgres` servisini `docker-compose.yml`'den kaldır, container'ı yeniden başlat.
2. `docker compose logs authz --tail=30` çalıştır. **Beklenen:** Loglarda `datasource`/`JPA`/`Hibernate` ile ilgili **hiçbir satır yok**; yalnızca `LDAP repositories`, `Redis repositories`, `Tomcat started` gibi satırlar var; `Started DocmostAuthzApplication` ile temiz başlıyor.
3. Oturumu kapat, `http://ingwiki:3000/login`'da `ali`/`ali123` ile LDAP butonuna tıkla. **Beklenen:** `/home`'a yönlendirilir (regresyon yok — `authenticate()` endpoint'i ve B11 space-otomatik-üyeliği etkilenmedi).
4. `docker compose logs docmost authz --tail=40 | grep -i -E "error|exception"` çalıştır. **Beklenen:** Boş sonuç (hiç hata/istisna yok).

## 4. Son Çalıştırma Sonuçları

| Tarih | §3.1 Sağlık | §3.2 Login (faruk/ali/ayse) | §3.3 Olumsuz | §3.4 Yetki matrisi | §3.5/§3.6 Browser | Not |
|---|---|---|---|---|---|---|
| İlk uçtan uca doğrulama (authz'nin ilk `docker compose up` sonrası canlı testi) | ✅ 200 | ✅ 200/200/200 | ✅ 401/401 | ✅ 200/403/200 | ✅ "Page not found" ekranı doğru | B3, B4, B5 hataları bu çalıştırmada bulunup düzeltildi (bkz. 03 §9) |
| Bu doküman oluşturulurken yapılan **tekrar** çalıştırma (authz imajı güncel koddan yeniden build edilip yeniden başlatıldıktan sonra) | ✅ `HTTP 200` + `{"status":"ok"}` | ✅ `200`/`200`/`200` | ✅ `401`/`401` | ✅ faruk `200`, ali `403`, ayse `200` | ✅ Aynı sayfaya tekrar girişte yine "Page not found" ekranı (ekran görüntüsüyle doğrulandı) | Regresyon yok; `docker-compose.override.yml` test sonrası kaldırılıp TLS/nginx yoluna (`https://docs.company.local` → `200`) geri dönüldü |
| Repo yeniden yapılandırması sonrası çalıştırma: önce her şey `docmost/ingwiki/`e taşındı, sonra `docmost/docmost` iç içeliği düzleştirilip tek `docmost/` (git kökü) haline getirildi (`git rev-parse --show-toplevel` → `/home/onder/dev/docmost` doğrulandı, `git log`/`git status` sağlam) | ✅ `HTTP 200` + `{"status":"ok"}` | ✅ `200`/`200`/`200` | — (önceki çalıştırmalarda zaten doğrulandı, tekrar edilmedi) | ✅ Compose proje adı (`ingwiki`) değişmediği için eski test sayfası/politika farklı bir Docker volume'da kalmıştı; yeni sayfa+politika oluşturulup matris baştan doğrulandı: faruk `200`, ali `403`, ayse `200` | — (bu çalıştırmada tekrar edilmedi) | `docker-compose.yml` içindeki `context: ..` yol değişikliği gerektirmedi (ingwiki hâlâ repo kökünün bir alt seviyesinde); yalnızca bu dokümandaki ve 03 §11'deki mutlak `cd` yolları `docmost/docmost/ingwiki` → `docmost/ingwiki` olarak düzeltildi |
| `ingwiki-v1.0` etiketi sonrası, JUnit + e2e script'in birlikte tam çalıştırılması (kullanıcı talebiyle: önce `mvn test`, sonra bu dosyadaki §3 senaryoları) | ✅ `HTTP 200` + `{"status":"ok"}` | ✅ `200`/`200`/`200` | ✅ `401`/`401` | ✅ faruk `200`, ali `403`, ayse `200` (`PAGE_ID=01a106ba-f322-7e6f-9e83-3cdb8c4c6c39`) | ✅ Yerel admin ile giriş yapılıp `Finance Only Page`'e tıklandı, yine "Page not found / you may not have access" ekranı (ekran görüntüsüyle doğrulandı) | `authz`: `mvn test` → 30/30 geçti, 0 hata. Regresyon yok; shim sonrası TLS/nginx yoluna geri dönüldü (`https://ingwiki` → `200`) |
| DNS/hosts kaydı `docs.company.local`'dan **`ingwiki`**'ye değiştirildi: `nginx/nginx.conf` (`server_name`), `docker-compose.yml` (`APP_URL`), `certs/*.pem` (CN/SAN yeniden üretildi), bu dosya ve 03 §11 güncellendi | ✅ `HTTP 200` + `{"status":"ok"}` (`https://ingwiki/`) | ✅ faruk `200` | — (bu çalıştırmada tekrar edilmedi) | — (bu çalıştırmada tekrar edilmedi) | — (bu çalıştırmada tekrar edilmedi) | Yalnızca `nginx` ve `docmost` container'ları `--force-recreate` ile yeniden başlatıldı; `authz`/Postgres/Redis/LDAP etkilenmedi |
| **Frontend LDAP giriş desteği eklendi (03 §9 B8, §8 L7 çözüldü)**: `login-form.tsx`'e ayrı bir LDAP giriş formu eklendi, `docmost` imajı yeniden build edilip yeniden başlatıldı | ✅ `HTTP 200` + `{"status":"ok"}` | — (bu çalıştırmada curl yerine §3.6 ile browser'dan test edildi) | — (tekrar edilmedi) | — (tekrar edilmedi) | ✅ **§3.6 ilk kez çalıştırıldı**: `faruk`/`faruk123` ile gerçek web formundan (LDAP bölümü) giriş yapıldı → `/home`'a yönlendirildi → `Finance Only Page` içeriği (DOCMOST-ADMIN bypass ile) başarıyla görüntülendi, ekran görüntüsüyle kanıtlandı | İlk kez: LDAP girişi API değil, gerçek tarayıcı formu üzerinden uçtan uca doğrulandı |
| **Login formu tek-form/iki-buton olarak yeniden düzenlendi (03 §9 B9)**: ayrı iki `<form>` yerine, paylaşılan "Email or username" alanı + iki alt alta submit butonu | ✅ `HTTP 200` | — | — | — | ✅ **§3.6 adım 3/7/8/9 tam çalıştırıldı**: (3) `faruk`/`faruk123` + LDAP butonu → `/home` + admin-bypass ile sayfa görüntülendi; (7) `faruk`/yanlış parola + LDAP butonu → kırmızı `"Invalid LDAP credentials"` alert'i (ekran görüntüsüyle doğrulandı); (8) `faruk` (e-posta değil) + "Sign In" butonu → API'ye hiç gitmeden inline `"Enter a valid email"` hatası; (9) `admin@placeholder.test`/`Password123!` + "Sign In" → `/home` | Dördü de headless browser'da art arda, aynı oturumda test edildi; regresyon yok |
| **`users.auth_source` kolonu eklendi, LDAP kullanıcıları parolasız provision ediliyor (03 §9 B10)**: yeni migration `20261004T140000-add-auth-source-to-users`, `docmost` imajı yeniden build edilip yeniden başlatıldı; mevcut `faruk/ali/ayse` demo kullanıcıları `auth_source='ldap', password=NULL` olacak şekilde elle güncellendi (SQL `UPDATE`, `DELETE` FK kısıtı yüzünden başarısız oldu) | ✅ `HTTP 200` + migration log'da `"Migration ... executed successfully"` | — | — | — | ✅ **§3.7 tam çalıştırıldı**: (1) SQL ile `auth_source`/`password` doğrulandı; (2) `faruk`/`faruk123` + LDAP butonu → `/home` (regresyon yok); (3) `faruk@placeholder.test` + rastgele parola + "Sign In" (şifre) butonu → temiz `401` + `"Email or password does not match"`, `docker compose logs docmost` içinde hata/istisna yok | `UserRepo.insertUser()` artık yalnızca parola verilmişse hash'liyor; `AuthService.login()`/`changePassword()`'a null-parola erken-çıkışı eklendi |
| **LDAP grup tabanlı otomatik space üyeliği eklendi (03 §9 B11)**: yeni `LdapSpaceProvisionService`, `docmost` imajı iki kez build edildi (ilk sürümde `getUserSpaceRoles()`'in `undefined` döndürme davranışı gözden kaçtığı için `500` hatası alındı, `existingRoles?.length` ile düzeltildi). **Not:** Bu çalıştırmadan önce kullanıcı workspace'i kendi tarayıcısından sıfırlayıp yeniden kurmuştu (`INGWiki` / `devops@ing.com.tr`); eski `admin@placeholder.test`/`faruk`/`ayse` kullanıcıları ve `Finance Only Page` artık yok, yalnızca `ali` LDAP ile yeniden provision edildi | ✅ `HTTP 200` | — | — | — | ✅ **§3.8 tam çalıştırıldı**: test LDAP'a `INGWIKI_SPACE1` grubu (üye: `ali`) eklendi (`ldap-seed` yeniden çalıştırıldı); `INGWIKI-SPACE1`/`INGWIKI-SPACE2` space'leri SQL ile oluşturuldu; `ali`/`ali123` LDAP girişi → `/home`; SQL ile doğrulandı: `ali` yalnızca `INGWIKI-SPACE1`'e `writer` olarak eklendi, `INGWIKI-SPACE2`'de üyelik yok; `/spaces` listesinde yalnızca `General`+`INGWIKI-SPACE1` göründü; `INGWIKI-SPACE1` içinde "New page"/"Create page"/"Space settings" butonları (native writer yetkisi çalışıyor); `http://ingwiki:3000/s/ingwiki-space2` → temiz `404` | İlk denemedeki `500` hatası ve düzeltmesi dahil tam süreç ekran görüntüsü/log'larla kanıtlandı; shim sonrası TLS/nginx yoluna geri dönüldü (`https://ingwiki` → `200`) |

## 5. Bu Script'i Güncel Tutma Kuralı

Bu dosya, kod her değiştiğinde **yeniden çalıştırılıp** §4 tablosuna yeni bir satır eklenerek güncellenmelidir (eski satırlar silinmez — regresyon geçmişi olarak kalır). `PAGE_ID` gibi ortam-özel değerler değişirse bu dosyadaki komutlar da güncellenmelidir. Yeni bir FR/senaryo eklenirse §3'e yeni bir alt başlık eklenir; mimari açıklama [03-final-architecture-and-use-cases.md](./03-final-architecture-and-use-cases.md)'e, gereksinim metni [02-requirements-1.md](./02-requirements-1.md)'e eklenir (burada tekrar edilmez).

---
Bu doküman serisinin tamamı: [01-docmost-oss-architecture.md](./01-docmost-oss-architecture.md) → [02-requirements-1.md](./02-requirements-1.md) → [03-final-architecture-and-use-cases.md](./03-final-architecture-and-use-cases.md) → 04 (bu doküman).
