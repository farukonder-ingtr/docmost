# ingwiki

**Sürüm: `ingwiki-v1.0`**

Bu klasör, [Docmost](https://github.com/docmost/docmost) açık kaynak (AGPL-3.0) wiki uygulamasının **üzerine** eklenen, LDAP kimlik doğrulama ve LDAP grubuna göre otomatik space üyeliği ekleyen bağımsız bir modüldür. Docmost'un kendisi (bu reponun `ingwiki/` dışındaki her yeri: `apps/`, `packages/`, `Dockerfile`, vb.) **değiştirilmez bir upstream kopyasıdır**; bu modül ona ek bir katman olarak dışarıdan entegre olur (bkz. "Upstream ile ilişki" bölümü).

## Bu modül ne ekliyor?

- `authz/` — bağımsız, **veritabanısız** bir Spring Boot 4.1.1 / Java 25 servisi: yalnızca LDAP bind doğrulama + grup çözümleme (Redis önbellekli).
- Docmost sunucu kodunda (`apps/server/src/integrations/ldap-authz/`, `apps/server/src/core/auth/`) hedefli, küçük değişiklikler: `POST /api/auth/ldap-login` endpoint'i, LDAP kullanıcılarının parolasız provision edilmesi, ve LDAP grubuna göre otomatik space üyeliği (`LdapSpaceProvisionService`). Sayfa/space erişimi tamamen Docmost'un kendi native `SpaceRole` sistemine bırakılmıştır.
- `docker-compose.yml`, `nginx/`, `certs/`, `.env.example` — tüm sistemi tek komutla ayağa kaldıran dağıtım tanımı.
- `test/ldap/` — geliştirme/test için gerçek AD yerine kullanılan `osixia/openldap` tabanlı test dizini (seed kullanıcılar/gruplar).
- `spec/` — **tüm mimari/gereksinim/test dokümantasyonu burada**:
  1. [spec/01-docmost-oss-architecture.md](./spec/01-docmost-oss-architecture.md) — Docmost OSS'un (değiştirilmemiş haliyle) mimarisi.
  2. [spec/02-requirements-1.md](./spec/02-requirements-1.md) — bu modülün gereksinimleri (FR/NFR).
  3. [spec/03-final-architecture-and-use-cases.md](./spec/03-final-architecture-and-use-cases.md) — uygulanmış son mimari ve use case'ler.
  4. [spec/04-headless-browser-e2e-tests.md](./spec/04-headless-browser-e2e-tests.md) — tekrarlanabilir uçtan uca test script'i.

## Hızlı başlangıç

```bash
cd ingwiki
cp .env.example .env   # secret'ları openssl rand -hex 32 ile doldurun
docker compose up -d
```

Detaylı kurulum/test adımları için → [spec/04-headless-browser-e2e-tests.md](./spec/04-headless-browser-e2e-tests.md). `authz` servisinin birim testleri için → `cd authz && mvn clean test`.

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

`ingwiki/` klasörü upstream'de bulunmadığı için bu merge'ler normalde `ingwiki/` içeriğiyle çakışmaz. Yalnızca `apps/server/src/integrations/ldap-authz/`, `apps/server/src/core/auth/` gibi **doğrudan değiştirilmiş Docmost dosyalarında** merge çakışması (conflict) beklenebilir — bu dosyaların listesi [spec/03-final-architecture-and-use-cases.md](./spec/03-final-architecture-and-use-cases.md)'de belgelenmiştir.

## Versiyonlama

Bu modül, Docmost'un kendi sürüm numarasından **bağımsız** olarak `ingwiki-vX.Y` biçiminde etiketlenir (git tag). Mevcut etiket: **`ingwiki-v1.0`** — LDAP girişi, LDAP grubuna göre otomatik space üyeliği, JUnit test paketi ve uçtan uca canlı doğrulamanın tamamlandığı ilk kararlı sürümü işaret eder (bkz. [spec/03-final-architecture-and-use-cases.md](./spec/03-final-architecture-and-use-cases.md)).

```bash
git tag -l 'ingwiki-*'        # bu modülün sürümlerini listeler
git log ingwiki-v1.0 -1       # v1.0 etiketinin işaret ettiği commit
```
