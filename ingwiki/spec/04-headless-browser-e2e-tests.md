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

- **A) `curl` ile API testleri** — `https://docs.company.local` (self-signed sertifika, `-k` ile) üzerinden, TLS/nginx dahil tam yoldan.
- **B) Headless browser testleri** — Chromium tabanlı araç self-signed sertifikaya güvenmediği için (`ERR_CERT_AUTHORITY_INVALID`), yalnızca bu adım için geçici bir **düz HTTP dev shim** kullanılır (bkz. §2).

## 2. Headless Browser İçin Geçici HTTP Shim

Nginx/TLS yolunu bozmadan, yalnızca tarayıcı testi süresince `docmost` servisini `docs.company.local:3000` üzerinden düz HTTP ile de erişilebilir kılan bir `docker-compose.override.yml`:

```yaml
# Temporary, local-only: lets the headless browser verify the UI over plain HTTP
# (it refuses the self-signed nginx cert). Remove this file to go back to the
# TLS/nginx entrypoint, which is the intended production-like path.
services:
  docmost:
    environment:
      APP_URL: http://docs.company.local:3000
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
curl -sk -o /dev/null -w "HTTP %{http_code}\n" https://docs.company.local/
curl -sk https://docs.company.local/api/health
```
**Beklenen:** İlki `200`; ikincisi `{"status":"ok",...,"database":{"status":"up"},"redis":{"status":"up"}}`.

### 3.2 LDAP ile Giriş (FR-1, FR-2, FR-3)

```bash
curl -sk -X POST https://docs.company.local/api/auth/ldap-login \
  -H 'Content-Type: application/json' -c /tmp/faruk.txt \
  -d '{"username":"faruk","password":"faruk123"}' -o /dev/null -w 'HTTP %{http_code}\n'
curl -sk -X POST https://docs.company.local/api/auth/ldap-login \
  -H 'Content-Type: application/json' -c /tmp/ali.txt \
  -d '{"username":"ali","password":"ali123"}' -o /dev/null -w 'HTTP %{http_code}\n'
curl -sk -X POST https://docs.company.local/api/auth/ldap-login \
  -H 'Content-Type: application/json' -c /tmp/ayse.txt \
  -d '{"username":"ayse","password":"ayse123"}' -o /dev/null -w 'HTTP %{http_code}\n'
```
**Beklenen:** Üçü de `200` ve bir `authToken` çerezi döner (cookie dosyalarında görülebilir).

### 3.3 Olumsuz Senaryolar (FR-4)

```bash
curl -sk -X POST https://docs.company.local/api/auth/ldap-login -H 'Content-Type: application/json' \
  -d '{"username":"faruk","password":"wrong"}' -o /dev/null -w 'HTTP %{http_code}\n'
curl -sk -X POST https://docs.company.local/api/auth/ldap-login -H 'Content-Type: application/json' \
  -d '{"username":"nobody","password":"x"}' -o /dev/null -w 'HTTP %{http_code}\n'
```
**Beklenen:** İkisi de `401`, farklı bir hata mesajı/kod sızdırmaz (kullanıcı adı var/yok ayrımı yapılamaz).

### 3.4 Sayfa Yetkilendirme Matrisi (FR-5 — FR-10)

Daha önce oluşturulmuş `Finance Only Page` kaynağına `DOCMOST-FINANCE → VIEW` politikası tanımlıdır (bkz. 03, §5.3). `PAGE_ID` değeri ortam başına sabittir; her Docker volume sıfırlandığında (ör. compose proje adı değiştiğinde) sayfa+politika yeniden oluşturulmalı ve buradaki değer güncellenmelidir — en güncel değer: `01a106ba-f322-7e6f-9e83-3cdb8c4c6c39` (bkz. §4 son satır).

```bash
PAGE_ID=01a10677-7764-7750-8c7a-0f942c3c8afa

curl -sk -X POST https://docs.company.local/api/pages/info -H 'Content-Type: application/json' \
  -b /tmp/faruk.txt -d "{\"pageId\":\"$PAGE_ID\"}" -o /dev/null -w 'faruk  -> HTTP %{http_code}\n'
curl -sk -X POST https://docs.company.local/api/pages/info -H 'Content-Type: application/json' \
  -b /tmp/ali.txt -d "{\"pageId\":\"$PAGE_ID\"}" -o /dev/null -w 'ali    -> HTTP %{http_code}\n'
curl -sk -X POST https://docs.company.local/api/pages/info -H 'Content-Type: application/json' \
  -b /tmp/ayse.txt -d "{\"pageId\":\"$PAGE_ID\"}" -o /dev/null -w 'ayse   -> HTTP %{http_code}\n'
```
**Beklenen:** `faruk` (DOCMOST-ADMIN bypass) → `200`; `ali` (DOCMOST-HR, eşleşmiyor) → `403`; `ayse` (DOCMOST-FINANCE, eşleşiyor) → `200`.

### 3.5 Headless Browser — Giriş ve Sayfa Erişimi (UI katmanı)

Headless browser aracı ile (bkz. §2'deki shim aktifken):

1. `http://docs.company.local:3000/login` adresini aç.
2. Workspace'in yerel (LDAP dışı) admin hesabıyla giriş yap (`email`/`password` alanlarına yaz, "Sign In" tıkla).
3. `/home`'a yönlendiğini ve sayfanın gerçek Docmost arayüzünü render ettiğini doğrula (ekran görüntüsü).
4. Kenar çubuğundan `Finance Only Page`'e tıkla.
5. **Beklenen:** Sayfa içeriği yerine Docmost'un kendi "Page not found / you may not have access" ekranı görünür — çünkü bu hesabın LDAP karşılığı yoktur ve LDAP katmanı fail-closed çalışır (bkz. 03, §8 L4).

Bu adım, backend yetkilendirmesinin yalnızca API seviyesinde değil, **gerçek kullanıcı arayüzünde de** doğru şekilde yansıdığını kanıtlar (yanlışlıkla sayfa içeriğinin SPA cache'inden sızmadığını gösterir).

## 4. Son Çalıştırma Sonuçları

| Tarih | §3.1 Sağlık | §3.2 Login (faruk/ali/ayse) | §3.3 Olumsuz | §3.4 Yetki matrisi | §3.5 Browser | Not |
|---|---|---|---|---|---|---|
| İlk uçtan uca doğrulama (authz'nin ilk `docker compose up` sonrası canlı testi) | ✅ 200 | ✅ 200/200/200 | ✅ 401/401 | ✅ 200/403/200 | ✅ "Page not found" ekranı doğru | B3, B4, B5 hataları bu çalıştırmada bulunup düzeltildi (bkz. 03 §9) |
| Bu doküman oluşturulurken yapılan **tekrar** çalıştırma (authz imajı güncel koddan yeniden build edilip yeniden başlatıldıktan sonra) | ✅ `HTTP 200` + `{"status":"ok"}` | ✅ `200`/`200`/`200` | ✅ `401`/`401` | ✅ faruk `200`, ali `403`, ayse `200` | ✅ Aynı sayfaya tekrar girişte yine "Page not found" ekranı (ekran görüntüsüyle doğrulandı) | Regresyon yok; `docker-compose.override.yml` test sonrası kaldırılıp TLS/nginx yoluna (`https://docs.company.local` → `200`) geri dönüldü |
| Repo yeniden yapılandırması sonrası çalıştırma: önce her şey `docmost/ingwiki/`e taşındı, sonra `docmost/docmost` iç içeliği düzleştirilip tek `docmost/` (git kökü) haline getirildi (`git rev-parse --show-toplevel` → `/home/onder/dev/docmost` doğrulandı, `git log`/`git status` sağlam) | ✅ `HTTP 200` + `{"status":"ok"}` | ✅ `200`/`200`/`200` | — (önceki çalıştırmalarda zaten doğrulandı, tekrar edilmedi) | ✅ Compose proje adı (`ingwiki`) değişmediği için eski test sayfası/politika farklı bir Docker volume'da kalmıştı; yeni sayfa+politika oluşturulup matris baştan doğrulandı: faruk `200`, ali `403`, ayse `200` | — (bu çalıştırmada tekrar edilmedi) | `docker-compose.yml` içindeki `context: ..` yol değişikliği gerektirmedi (ingwiki hâlâ repo kökünün bir alt seviyesinde); yalnızca bu dokümandaki ve 03 §11'deki mutlak `cd` yolları `docmost/docmost/ingwiki` → `docmost/ingwiki` olarak düzeltildi |

## 5. Bu Script'i Güncel Tutma Kuralı

Bu dosya, kod her değiştiğinde **yeniden çalıştırılıp** §4 tablosuna yeni bir satır eklenerek güncellenmelidir (eski satırlar silinmez — regresyon geçmişi olarak kalır). `PAGE_ID` gibi ortam-özel değerler değişirse bu dosyadaki komutlar da güncellenmelidir. Yeni bir FR/senaryo eklenirse §3'e yeni bir alt başlık eklenir; mimari açıklama [03-final-architecture-and-use-cases.md](./03-final-architecture-and-use-cases.md)'e, gereksinim metni [02-requirements-1.md](./02-requirements-1.md)'e eklenir (burada tekrar edilmez).

---
Bu doküman serisinin tamamı: [01-docmost-oss-architecture.md](./01-docmost-oss-architecture.md) → [02-requirements-1.md](./02-requirements-1.md) → [03-final-architecture-and-use-cases.md](./03-final-architecture-and-use-cases.md) → 04 (bu doküman).
