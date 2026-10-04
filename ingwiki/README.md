# ingwiki

**Sürüm: `ingwiki-v1.0`**

Bu klasör, [Docmost](https://github.com/docmost/docmost) açık kaynak (AGPL-3.0) wiki uygulamasının **üzerine** eklenen, LDAP kimlik doğrulama ve sayfa-bazlı (LDAP grup → sayfa) yetkilendirme ekleyen bağımsız bir modüldür. Docmost'un kendisi (bu reponun `ingwiki/` dışındaki her yeri: `apps/`, `packages/`, `Dockerfile`, vb.) **değiştirilmez bir upstream kopyasıdır**; bu modül ona ek bir katman olarak dışarıdan entegre olur (bkz. "Upstream ile ilişki" bölümü).

## Bu modül ne ekliyor?

- `authz/` — bağımsız bir Spring Boot 4.1.1 / Java 25 yetkilendirme servisi (LDAP bind, grup çözümleme, kaynak/politika veritabanı, admin API).
- Docmost sunucu kodunda (`apps/server/src/integrations/ldap-authz/`, `apps/server/src/core/auth/`, `apps/server/src/core/page/page-access/`) hedefli, küçük değişiklikler: `POST /api/auth/ldap-login` endpoint'i ve mevcut `PageAccessService` yetkilendirme çekirdeğine LDAP kontrolü eklenmesi.
- `docker-compose.yml`, `nginx/`, `certs/`, `.env.example` — tüm sistemi tek komutla ayağa kaldıran dağıtım tanımı.
- `test/ldap/` — geliştirme/test için gerçek AD yerine kullanılan `osixia/openldap` tabanlı test dizini (seed kullanıcılar/gruplar).
- `spec/` — **tüm mimari/gereksinim/test dokümantasyonu burada**:
  1. [spec/01-docmost-oss-architecture.md](./spec/01-docmost-oss-architecture.md) — Docmost OSS'un (değiştirilmemiş haliyle) mimarisi.
  2. [spec/02-requirements-1.md](./spec/02-requirements-1.md) — bu modülün gereksinimleri (FR/NFR).
  3. [spec/03-final-architecture-and-use-cases.md](./spec/03-final-architecture-and-use-cases.md) — uygulanmış son mimari, use case'ler, bulunan hatalar, orijinal taslaktan sapmalar.
  4. [spec/04-headless-browser-e2e-tests.md](./spec/04-headless-browser-e2e-tests.md) — tekrarlanabilir uçtan uca test script'i ve çalıştırma sonuçları.

## Hızlı başlangıç

```bash
cd ingwiki
cp .env.example .env   # secret'ları openssl rand -hex 32 ile doldurun
docker compose up -d
```

Detaylı kurulum/test adımları için → [spec/04-headless-browser-e2e-tests.md](./spec/04-headless-browser-e2e-tests.md). `authz` servisinin birim testleri için → `cd authz && mvn test`.

## Upstream ile ilişki

Bu repo iki uzak (remote) ile çalışır:

```bash
git remote -v
# origin    -> bu fork (kendi geliştirmelerinizi buraya push edersiniz)
# upstream  -> https://github.com/docmost/docmost.git (resmi Docmost OSS, salt-okunur kaynak)
```

Docmost çekirdeğini güncel tutmak için periyodik olarak upstream'den çekin:

```bash
git fetch upstream
git merge upstream/main   # veya: git rebase upstream/main
```

`ingwiki/` klasörü upstream'de bulunmadığı için bu merge'ler normalde `ingwiki/` içeriğiyle çakışmaz. Yalnızca `apps/server/src/integrations/ldap-authz/`, `apps/server/src/core/auth/`, `apps/server/src/core/page/page-access/` gibi **doğrudan değiştirilmiş Docmost dosyalarında** merge çakışması (conflict) beklenebilir — bu dosyaların listesi [spec/03-final-architecture-and-use-cases.md](./spec/03-final-architecture-and-use-cases.md) §4 ve §10'da belgelenmiştir.

## Versiyonlama

Bu modül, Docmost'un kendi sürüm numarasından **bağımsız** olarak `ingwiki-vX.Y` biçiminde etiketlenir (git tag). Mevcut etiket: **`ingwiki-v1.0`** — LDAP girişi, sayfa-bazlı yetkilendirme, yönetim API'si, JUnit test paketi (30 test) ve uçtan uca canlı doğrulamanın tamamlandığı ilk kararlı sürümü işaret eder (bkz. [spec/03-final-architecture-and-use-cases.md](./spec/03-final-architecture-and-use-cases.md) §9 "Geliştirme Sürecinde Bulunan ve Düzeltilen Sorunlar").

```bash
git tag -l 'ingwiki-*'        # bu modülün sürümlerini listeler
git log ingwiki-v1.0 -1       # v1.0 etiketinin işaret ettiği commit
```
