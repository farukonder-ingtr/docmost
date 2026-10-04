# Requirements-1: LDAP Kimlik Doğrulama ve Sayfa-Bazlı Yetkilendirme

> Bu doküman, [01-docmost-oss-architecture.md](./01-docmost-oss-architecture.md)'de tanımlanan Docmost OSS temeline eklenmesi gereken gereksinimleri tanımlar. Kaynak: `docmost-ldap-page-authorization.md`. Uygulanmış/doğrulanmış son hal için → [03-final-architecture-and-use-cases.md](./03-final-architecture-and-use-cases.md).

## 1. Problem Tanımı

Docmost OSS'ta (bkz. §6, doküman 01) kimlik doğrulama yalnızca Docmost'un kendi email/parola veritabanı ile yapılır ve yetkilendirme space-rolü + opsiyonel page-permission ile sınırlıdır. Kurumsal bir ortamda:

1. Kullanıcıların kurumsal dizin (LDAP/Active Directory) üzerinden kimliklenmesi,
2. Sayfa erişiminin **LDAP grup üyeliğine** göre otomatik ve merkezi olarak yönetilmesi

gerekmektedir. Docmost'un ticari sürümü bu iki özelliği sunar; AGPL-3.0 OSS çekirdeğini ücretsiz tutarak aynı ihtiyacı karşılamak için Docmost **fork'lanır** ve yanına bağımsız bir **yetkilendirme servisi** eklenir.

## 2. Aktörler

| Aktör | Tanım |
|---|---|
| Son kullanıcı | LDAP/AD hesabıyla Docmost'a giriş yapan, sayfalara erişen kişi |
| LDAP/AD Yöneticisi | Kullanıcı/grup üyeliğini dizin üzerinde yöneten IT personeli |
| İçerik/Yetki Yöneticisi | Hangi sayfaya hangi LDAP grubunun hangi izinle erişeceğini tanımlayan kişi |
| Docmost (fork) | Kimlik doğrulama isteğini ve her kaynak erişim isteğini yetkilendirme servisine danışan uygulama |
| Yetkilendirme Servisi | LDAP bind + grup çözümleme + politika (policy) değerlendirmesi yapan bağımsız servis |

## 3. Hedefler (Goals)

- G1: Kullanıcılar, Docmost'un kendi parola veritabanı yerine kurumsal LDAP/AD kimlik bilgileriyle giriş yapabilmeli.
- G2: İlk LDAP girişinde kullanıcı otomatik olarak Docmost'ta provision edilmeli (manuel hesap açma gerekmemeli).
- G3: Sayfa (ve dolayısıyla space/attachment/yorum) erişimi, **LDAP grubu → izin (VIEW/EDIT/ADMIN)** eşlemesiyle yönetilebilmeli.
- G4: Üst sayfadaki (ebeveyn) kısıtlama, politika tanımlanmamış alt sayfalara da miras geçmeli (restricted parent ⇒ restricted children).
- G5: Yetkilendirme kararı LDAP grup üyeliği **değiştiğinde** (kullanıcıya grup eklenmesi/çıkarılması), en geç tanımlı bir önbellek (cache) süresi içinde yansımalı.
- G6: Belirli bir LDAP grubu (ör. `DOCMOST-ADMIN`) tüm kaynaklarda otomatik tam yetkiye sahip olmalı (her sayfa için ayrı politika tanımlamaya gerek kalmamalı).
- G7: Yetkilendirme kararını veren servisin yönetimi (hangi sayfaya hangi grup, hangi izin) bir API üzerinden yapılabilmeli.
- G8: Sistem **AGPL-3.0 OSS Docmost çekirdeğini** kullanmaya devam etmeli; ticari lisansa geçiş zorunlu olmamalı.

## 4. Hedef Olmayanlar (Non-Goals)

- NG1: Keycloak, OAuth2 proxy veya ikinci bir IAM/identity broker kurulması **hedeflenmemektedir** — LDAP/AD tek kimlik kaynağıdır.
- NG2: Docmost önüne salt bir reverse-proxy tabanlı URL-authorization (ör. yalnızca Nginx ile path koruma) **yeterli kabul edilmemektedir**; çünkü API/attachment/search/WebSocket gibi farklı erişim yolları aynı modeli paylaşmalıdır.
- NG3: LDAP şifresinin Docmost veritabanında saklanması **yapılmayacaktır**.
- NG4: Nested (iç içe) AD gruplarının otomatik/recursive çözümlenmesi bu sürümün kapsamı dışındadır (bkz. §7, kısıt K4).

## 5. Fonksiyonel Gereksinimler

### 5.1 Kimlik Doğrulama (Authentication)

- **FR-1**: Sistem, kullanıcı adı/parola ile LDAP bind doğrulaması yapabilen bir endpoint sunmalıdır.
- **FR-2**: Başarılı LDAP doğrulamasından sonra Docmost, kendi native oturumunu (session/JWT) oluşturmalı; LDAP servisinin oturum çerezi/token'ı Docmost tarafından **kullanılmamalıdır**.
- **FR-3**: LDAP'ta bulunan ancak Docmost'ta henüz kaydı olmayan kullanıcı, ilk başarılı girişte otomatik olarak workspace'e üye yapılmalı (provisioning).
- **FR-4**: Hatalı parola ve var olmayan kullanıcı adı için **aynı** hata mesajı dönmeli (kullanıcı adı sızdırılmamalı).

### 5.2 Yetkilendirme (Authorization)

- **FR-5**: Her kaynak erişim isteğinde (sayfa görüntüleme/düzenleme), istek sahibinin LDAP grupları ile kaynağa tanımlı politikalar karşılaştırılarak VIEW/EDIT/ADMIN kararı üretilmelidir.
- **FR-6**: Bir kaynağa hiçbir politika tanımlanmamışsa (unmanaged resource), erişim **reddedilmemeli** (Docmost'un kendi space/page-permission modeli geçerli kalmalı) — LDAP katmanı yalnızca **ek bir kısıtlama katmanı**dır, var olan izinleri genişletmez.
- **FR-7**: Bir kaynağa (sayfaya) politika tanımlıysa, kullanıcının grupları bu politikalarla kesişmiyorsa erişim reddedilmelidir.
- **FR-8**: Üst kaynakta (ebeveyn sayfa) kısıtlama varsa ve kullanıcı buna uymuyorsa, alt kaynak için ayrıca politika tanımlanmamış olsa bile erişim reddedilmelidir (hiyerarşik miras, bkz. G4).
- **FR-9**: `DOCMOST-ADMIN` (veya yapılandırılabilir eşdeğer) grubundaki kullanıcılar tüm kaynaklara otomatik ADMIN erişimine sahip olmalıdır.
- **FR-10**: VIEW/EDIT/ADMIN izin hiyerarşisi şu şekilde olmalıdır: ADMIN ⊇ EDIT ⊇ VIEW (ADMIN her isteği karşılar, EDIT sadece VIEW+EDIT isteklerini karşılar).

### 5.3 Yönetim (Admin API)

- **FR-11**: Yetkilendirme servisi, bir kaynağı (PAGE/SPACE) ve onun üst kaynağını tanımlayan bir API sunmalıdır.
- **FR-12**: Yetkilendirme servisi, bir kaynağa LDAP grubu + izin seviyesi ekleme/listeleme/silme API'si sunmalıdır.
- **FR-13**: Yönetim ve yetkilendirme/kimlik doğrulama API'leri yalnızca Docmost backend'inden, paylaşılan bir **gizli anahtar (shared secret)** ile erişilebilir olmalı; doğrudan internete açılmamalıdır.

### 5.4 Önbellekleme ve Tutarlılık

- **FR-14**: LDAP grup sorguları, her istek için dizine gitmemek adına önbelleğe alınmalı; önbellek TTL'si yapılandırılabilir olmalıdır.
- **FR-15**: LDAP dizinine erişilemiyorsa (bağlantı hatası/timeout), karar **"erişim yok" (deny)** olmalı — "son bilinen izinlerle devam et" (fail-open) **yasaktır** (bkz. NFR güvenlik kuralı S11).

### 5.5 Kapsam (Hangi Docmost Kaynakları Korunmalı)

- **FR-16**: Sayfa görüntüleme/güncelleme/silme/taşıma işlemleri LDAP yetkilendirmesinden geçmelidir.
- **FR-17**: Attachment (dosya/görsel) erişimi, bağlı olduğu sayfanın LDAP yetkilendirmesinden geçmelidir.
- **FR-18**: Arama sonuçları, kullanıcının LDAP yetkilendirmesi reddettiği sayfaları **içermemelidir**.
- **FR-19**: WebSocket/realtime collaboration bağlantıları, bağlanılan sayfa için LDAP yetkilendirmesinden geçmelidir.
- **FR-20**: Public sharing (herkese açık link) özelliği, LDAP tabanlı yetkilendirme modeliyle aynı güvenlik sınırında olmadığından devre dışı bırakılması **önerilir**.

## 6. Fonksiyonel Olmayan Gereksinimler (NFR)

### 6.1 Güvenlik (değişmez kurallar)

| # | Kural |
|---|---|
| S1 | LDAP parolası hiçbir zaman Docmost veritabanına yazılmaz. |
| S2 | LDAP bind hesabı salt-okunur (read-only) olmalıdır. |
| S3 | PostgreSQL (her iki veritabanı) dışarı açılmaz. |
| S4 | Redis dışarı açılmaz. |
| S5 | Yetkilendirme servisinin iç (internal) endpoint'leri dışarı açılmaz. |
| S6 | Kullanıcı kimliğini taşıyan header'lara (ör. `X-Docmost-User`) doğrudan browser'dan güvenilmez; yalnızca Docmost→AuthZ dahili çağrısında, paylaşılan gizli anahtarla birlikte kullanılır. |
| S7 | Paylaşılan gizli anahtar yalnızca Docker/iç ağ üzerinden kullanılır. |
| S8 | Attachment erişimi yetkilendirilir (FR-17). |
| S9 | Arama sonuçları yetkilendirilir (FR-18). |
| S10 | WebSocket bağlantıları yetkilendirilir (FR-19). |
| S11 | Fail-open yapılmaz (FR-15). |
| S12 | Public sharing kapatılması önerilir (FR-20). |

### 6.2 Performans / Kullanılabilirlik

- NFR-1: Grup önbelleği TTL'si varsayılan 60 saniye ile 5 dakika arasında yapılandırılabilir olmalı; düşük TTL daha hızlı yetki iptali, yüksek TTL daha az LDAP yükü sağlar (trade-off açıkça belgelenmelidir).
- NFR-2: Yetkilendirme servisi, Docmost'un kritik yoluna (page read) eklendiği için yüksek oranda erişilebilir olmalı; servis arızasında davranış NFR güvenlik kuralı S11'e göre **deny** olmalıdır (fail-closed), kullanıcıya anlamlı bir hata (403) dönülmelidir.

### 6.3 Gözlemlenebilirlik

- NFR-3: Yetkilendirme reddi (deny) kararlarının nedeni (ör. "LDAP kullanıcı bulunamadı", "eşleşen grup yok") sunucu loglarında izlenebilir olmalıdır.

### 6.4 Test Edilebilirlik

- NFR-4: Yetkilendirme servisinin karar mantığı (admin bypass, politika eşleştirme, izin hiyerarşisi, ebeveyn mirası, fail-closed davranışı) gerçek bir LDAP/Docker ortamına ihtiyaç duymadan, birim testleriyle (mock'lanmış LDAP/veritabanı) doğrulanabilir olmalıdır — canlı ortam testi yalnızca ek bir doğrulama katmanı olmalı, **tek** doğrulama yöntemi olmamalıdır.

## 7. Kısıtlar (Constraints)

- K1: Docmost OSS (AGPL-3.0) fork edilerek değiştirilecektir; ticari Docmost sürümüne bağımlılık yoktur.
- K2: Yetkilendirme servisi Java/Spring Boot ile yazılacaktır (Spring Boot 4.1.1, Java 25 — en güncel sürümler).
- K3: Mimaride Keycloak/OAuth-proxy/ikinci IAM **kullanılmayacaktır** (NG1).
- K4: Nested AD group resolution (ör. `LDAP_MATCHING_RULE_IN_CHAIN`) bu sürümde ele alınmaz; yalnızca doğrudan (`member=`) grup üyeliği çözümlenir.
- K5: Docker Compose ile çalıştırılabilir olmalı; iç servisler (`authz`, iki Postgres, Redis, LDAP) yalnızca iç ağda, dışa yalnızca TLS sonlandıran bir reverse-proxy (Nginx) açılmalıdır.

## 8. Varsayımlar

- A1: Kurumsal LDAP/AD dizininde kullanıcılar ve gruplar önceden tanımlıdır; bu proje dizin içeriğini yönetmez, yalnızca tüketir.
- A2: Docmost kullanıcılarının email adresi, workspace içinde benzersizdir ve LDAP'taki `mail` özniteliğiyle eşleşir.
- A3: Test/geliştirme ortamında gerçek AD yerine `osixia/openldap` tabanlı bir test dizini kullanılabilir (bkz. doküman 03, §Test Ortamı).

## 9. Kabul Kriterleri (Doğrulama Senaryoları)

Her FR için asgari doğrulama senaryosu:

| Gereksinim | Doğrulama senaryosu |
|---|---|
| FR-1, FR-2, FR-3 | Daha önce Docmost'ta kaydı olmayan bir LDAP kullanıcısı doğru parola ile giriş yapar → yeni Docmost hesabı oluşur ve oturum açılır. |
| FR-4 | Yanlış parola ve var olmayan kullanıcı adı aynı HTTP 401 + aynı mesajı döner. |
| FR-5, FR-6, FR-7 | Politika tanımlanmamış sayfa herkese (space izni dahilinde) açık kalır; politika tanımlanmış sayfa yalnızca eşleşen gruba açılır. |
| FR-8 | Ebeveyn sayfada kısıtlama varken alt sayfada politika olmasa bile erişim reddedilir. |
| FR-9, FR-10 | `DOCMOST-ADMIN` grubundaki kullanıcı, hiçbir politika olmayan/eşleşmeyen sayfalarda dahi erişebilir. |
| FR-11, FR-12, FR-13 | Yönetim API'si gizli anahtar olmadan çağrıldığında reddedilir; doğru anahtarla kaynak/politika oluşturulabilir. |
| FR-14, FR-15 | LDAP servisi durdurulduğunda, önbellek süresi dolmuş kullanıcılar için erişim reddedilir (deny), "son bilinen" izinle devam edilmez. |
| FR-16, FR-17, FR-18, FR-19 | Yetkisiz kullanıcı için sayfa/attachment/arama/WS üzerinden erişim tutarlı biçimde reddedilir. |
| NFR-4 | Yetkilendirme servisinin `authorization`/`ldap`/`admin`/`config` paketleri için gerçek LDAP'a bağlanmadan çalışan bir JUnit test paketi mevcuttur ve `mvn test` ile çalıştırılabilir. |

## 10. Sözlük

| Terim | Anlam |
|---|---|
| LDAP grubu | Dizin üzerinde tanımlı, kullanıcıları gruplayan nesne (`groupOfNames`/AD `group`) |
| Kaynak (resource) | Yetkilendirme servisinde tanımlı PAGE veya SPACE |
| Politika (policy) | Bir kaynağa atanmış `(ldap_group, permission)` eşlemesi |
| Fail-closed | Belirsizlik/hata durumunda erişimi reddetme ilkesi |
| Provisioning | Kullanıcının ilk girişte otomatik hesap oluşturulması |

---
Bu gereksinimlerin uygulanmış ve test edilmiş hali için → [03-final-architecture-and-use-cases.md](./03-final-architecture-and-use-cases.md)
