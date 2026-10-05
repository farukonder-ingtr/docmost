# Requirements: LDAP Kimlik Doğrulama ve Grup-Tabanlı Space Üyeliği

> OSS temel mimarisi için → [01-docmost-oss-architecture.md](./01-docmost-oss-architecture.md). Uygulanmış mimari için → [03-final-architecture-and-use-cases.md](./03-final-architecture-and-use-cases.md).

## 1. Amaç

Docmost OSS, kimlik doğrulamayı yalnızca kendi email/parola veritabanıyla yapar. Bu proje, kurumsal bir LDAP/Active Directory dizinini **ikinci bir kimlik doğrulama yöntemi** olarak ekler:

1. Kullanıcılar LDAP bind ile giriş yapabilmeli.
2. İlk LDAP girişinde kullanıcı Docmost'a otomatik provision edilmeli.
3. Belirli bir LDAP grubunun üyesi olan kullanıcı, adlandırma kuralına uyan bir Docmost space'ine otomatik üye yapılmalı.
4. Bundan sonraki tüm erişim kontrolü Docmost'un **kendi native** space/page RBAC'ine bırakılmalı — ayrı bir LDAP yetkilendirme katmanı olmamalı.

## 2. Aktörler

| Aktör | Tanım |
|---|---|
| Son kullanıcı | LDAP veya Docmost yerel hesabıyla giriş yapan kişi |
| LDAP/AD Yöneticisi | Kullanıcı/grup üyeliğini dizin üzerinde yöneten IT personeli |
| Docmost | Kimlik doğrulama isteğini `authz` servisine danışan uygulama |
| `authz` servisi | Yalnızca LDAP bind doğrulama + grup çözümleme yapan, **veritabanı içermeyen** bağımsız servis |

## 3. Fonksiyonel Gereksinimler

### 3.1 Kimlik Doğrulama

- **FR-1**: Docmost, `POST /api/auth/ldap-login` ile kullanıcı adı/parolayı `authz` servisine ileterek LDAP bind doğrulaması yapabilmeli.
- **FR-2**: Başarılı LDAP doğrulamasından sonra Docmost kendi native oturumunu (session/cookie) oluşturmalı; bu oturum, yerel parola girişiyle oluşan oturumla **birebir aynı** şekilde davranmalı (aynı `createSessionAndToken` çağrısı).
- **FR-3**: LDAP'ta bulunan ama Docmost'ta kaydı olmayan kullanıcı, ilk başarılı girişte otomatik olarak mevcut workspace'e üye yapılmalı (varsayılan rol: `member`).
- **FR-4**: LDAP üzerinden provision edilen kullanıcı için Docmost `users` tablosunda **gerçek bir parola hash'i tutulmamalı**; kullanıcı `auth_source='ldap'`, `password=NULL` ile işaretlenmeli.
- **FR-5**: `password` alanı `NULL` olan bir kullanıcı, yerel parola girişini (`POST /api/auth/login`) denediğinde, sunucu tarafında hata/çökme olmadan temiz bir `401` ("Email or password does not match") almalı.
- **FR-6**: Hatalı parola ve var olmayan kullanıcı adı için LDAP girişinde **aynı** hata (401) dönmeli (kullanıcı adı sızdırılmamalı).

### 3.2 Giriş Formu Davranışı

- **FR-7**: Login ekranında **tek bir form ve tek bir buton** olmalı; kullanıcı hangi yöntemle (LDAP mı yerel mi) giriş yapacağını seçmek zorunda kalmamalı.
- **FR-8**: Giriş denemesi önce LDAP'ı, başarısız olursa yerel parola girişini denemeli (bu sıra sabittir).
- **FR-9**: İki yöntem de başarısız olursa kullanıcıya yalnızca genel bir **"Login failed"** mesajı gösterilmeli — hangi yöntemin neden başarısız olduğu (401/400/network vb.) hiçbir şekilde sızdırılmamalı.
- **FR-10**: Giriş alanı (kullanıcı adı/email) **e-posta formatında olmak zorunda olmamalı** — düz bir LDAP kullanıcı adı da kabul edilmeli.
- **FR-11**: Yerel girişte MFA gerekiyorsa veya (bulut ortamında) e-posta doğrulaması gerekiyorsa, bu özel akışlar genel "Login failed" ile ezilmemeli, kullanıcı ilgili sayfaya yönlendirilmeli.

### 3.3 Grup-Tabanlı Space Üyeliği

- **FR-12**: `INGWIKI_<AD>` adlı bir LDAP grubunun üyesi olan bir kullanıcı, LDAP ile her girişinde, aynı workspace içinde `INGWIKI-<AD>` adlı (alt çizgi→tire, case-insensitive) bir space varsa, oraya otomatik olarak `writer` rolüyle eklenmeli.
- **FR-13**: Bu eşleştirme **idempotent** olmalı (zaten üye olan tekrar eklenmemeli) ve **yalnızca ekleyici** olmalı (kullanıcı LDAP grubundan çıkarılsa bile mevcut space üyeliği geri alınmamalı).
- **FR-14**: Üyelik eklendikten sonra o space içindeki tüm işlemler (sayfa oluşturma/düzenleme, space ayarları vb.) tamamen Docmost'un kendi `SpaceRole` (admin/writer/reader) sistemi tarafından yönetilmeli — ayrı bir LDAP yetkilendirme kontrolü **olmamalı**.
- **FR-15**: Space-üyelik senkronizasyonu başarısız olursa (ör. DB hatası), hata loglanmalı ama login akışı **engellenmemeli**.

### 3.4 Yönetim ve Altyapı

- **FR-16**: `authz` servisi **hiçbir veritabanına bağımlı olmamalı** — yalnızca LDAP'a (ve grup önbelleği için Redis'e) bağlanmalı.
- **FR-17**: `authz`'nin `/internal/**` endpoint'leri yalnızca paylaşılan bir gizli anahtar (`X-Docmost-Internal-Secret` header'ı) ile erişilebilir olmalı; doğrudan internete açılmamalı.
- **FR-18**: LDAP grup sorguları Redis'te önbelleğe alınmalı; önbellek TTL'si yapılandırılabilir olmalı (varsayılan 60 saniye).
- **FR-19**: LDAP dizinine erişilemiyorsa giriş denemesi reddedilmeli (fail-closed) — "son bilinen durumla devam et" davranışı olmamalı.

## 4. Hedef Olmayanlar (Non-Goals)

- NG1: Sayfa/kaynak bazlı ayrı bir LDAP yetkilendirme katmanı (policy/resource modeli) — kaldırıldı, tekrar eklenmeyecek.
- NG2: Nested (iç içe) AD gruplarının otomatik çözümlenmesi — yalnızca doğrudan (`member=`) üyelik çözümlenir.
- NG3: Keycloak/OAuth-proxy gibi ikinci bir IAM/identity broker — LDAP/AD tek ek kimlik kaynağıdır.
- NG4: LDAP parolasının Docmost veritabanında saklanması.

## 5. Fonksiyonel Olmayan Gereksinimler

- **NFR-1**: `authz` servisinin LDAP bind/grup çözümleme mantığı, gerçek bir LDAP sunucusuna ihtiyaç duymadan (mock tabanlı) JUnit testleriyle doğrulanabilir olmalı.
- **NFR-2**: Dışarıya yalnızca TLS sonlandıran bir reverse-proxy (Nginx) açılmalı; Postgres'ler, Redis, `authz` ve LDAP yalnızca iç ağda olmalı.
- **NFR-3**: `authz` ile Docmost arası iletişim yalnızca Docker iç ağı üzerinden, paylaşılan gizli anahtarla yapılmalı.

## 6. Kısıtlar

- K1: Docmost OSS (AGPL-3.0) fork'u değiştirilir; ticari Docmost sürümüne bağımlılık yoktur.
- K2: `authz` servisi Spring Boot 4.1.1 / Java 25 ile yazılmıştır.
- K3: Docker Compose ile çalıştırılır; `authz`, Postgres, Redis, LDAP yalnızca iç ağdadır, dışarı yalnızca Nginx açılır.

## 7. Varsayımlar

- A1: LDAP/AD dizininde kullanıcılar ve gruplar önceden tanımlıdır; bu proje dizin içeriğini yönetmez, yalnızca tüketir.
- A2: Docmost kullanıcılarının email adresi workspace içinde benzersizdir ve LDAP'taki `mail` özniteliğiyle eşleşir.
- A3: Test/geliştirme ortamında gerçek AD yerine `osixia/openldap` tabanlı bir test dizini kullanılır (bkz. 03, §6).

## 8. Kabul Kriterleri

| Gereksinim | Doğrulama senaryosu |
|---|---|
| FR-1, FR-2, FR-3 | Docmost'ta kaydı olmayan bir LDAP kullanıcısı doğru parola ile giriş yapar → yeni hesap oluşur, oturum açılır. |
| FR-4, FR-5 | LDAP kullanıcısının `password` alanı `NULL`'dur; bu kullanıcı yerel girişi denerse sunucu çökmeden temiz `401` alır. |
| FR-6 | Yanlış parola ve var olmayan kullanıcı adı aynı `401` sonucunu verir. |
| FR-7, FR-8, FR-9, FR-10 | Tek buton, önce LDAP sonra yerel parola dener; ikisi de başarısız olursa genel "Login failed"; e-posta olmayan kullanıcı adı kabul edilir. |
| FR-12, FR-13, FR-14 | `INGWIKI_<AD>` grubundaki bir kullanıcı LDAP ile giriş yapınca yalnızca `INGWIKI-<AD>` space'ine `writer` olarak eklenir; tekrar girişte tekrar eklenmez; space içinde native yetkiler geçerlidir. |
| FR-16 | `authz` container'ı veritabanı bağlantısı olmadan başlar. |
| FR-17 | Gizli anahtar olmadan `/internal/**` çağrıları `403` ile reddedilir. |
| FR-19 | LDAP dizinine erişilemediğinde giriş denemesi reddedilir. |

---
Uygulanmış ve test edilmiş hali için → [03-final-architecture-and-use-cases.md](./03-final-architecture-and-use-cases.md)
