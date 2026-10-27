# PII-loggning – policy

> **Kärnregel:** Inga `partyId`, `personalNumber`/`legalId`, namn, e-post eller telefonnummer i
> klartext i loggar. Maska alltid med PII-maskeraren (`se.sundsvall.dept44.util.PiiMasker`) innan
> värdet loggas.
>
> **`LogUtils.sanitizeForLogging` räcker INTE.** Den skyddar bara mot *log injection* (CRLF och
> kontrolltecken som låter en angripare förfalska loggrader) – den döljer inte persondata. De två
> är kompletterande, inte utbytbara.

## Varför

`partyId` är en direkt identifierare mot Party-tjänsten och kan slås upp till personnummer.
Personnummer, samordningsnummer, namn, e-post och telefonnummer är personuppgifter enligt GDPR.
Loggar hamnar i centraliserad logghantering (ELK/GELF), lagras över tid och är läsbara för fler än
de som hanterar själva ärendet. Persondata i klartext i loggar är därför en incident som ska
undvikas, inte en bekvämlighet.

## Vad räknas som PII i det här sammanhanget

|                   Kategori                   |                Exempel                 |                         Maska?                         |
|----------------------------------------------|----------------------------------------|--------------------------------------------------------|
| `partyId` (UUID)                             | `f47ac10b-58cc-4372-a567-0e02b2c3d479` | **Ja**                                                 |
| Personnummer / samordningsnummer / `legalId` | `900101-1234`                          | **Ja**                                                 |
| E-postadress                                 | `john.doe@example.com`                 | **Ja**                                                 |
| Telefonnummer                                | `070-123 45 67`                        | **Ja**                                                 |
| Namn, adress                                 | `Anna Andersson`, `Storgatan 1`        | **Ja** – maska vid källan (regex fångar inte fri text) |
| Organisationsnummer                          | `5560000000`                           | Bedöm i sammanhanget – maska vid tveksamhet            |

## Så här gör du

Använd `PiiMasker` från `dept44-starter`. En generell ingång maskar alla kategorier i ett svep:

```java
import se.sundsvall.dept44.util.PiiMasker;

LOG.info("Inga kontaktinställningar hittades för {} med filter {}",
    PiiMasker.maskPii(partyId), filters);
// → "... för f47a... med filter {...}"
```

För ett värde vars typ är känd kan du använda en riktad metod – tydligare och billigare:

|               Metod                |                            Använd för                            |
|------------------------------------|------------------------------------------------------------------|
| `PiiMasker.maskPii(s)`             | Generellt – godtycklig sträng som kan innehålla flera kategorier |
| `PiiMasker.maskPii(s, kategorier)` | Som ovan, men bara de kategorier som anges (8.1.0+)              |
| `PiiMasker.maskUuid(s)`            | Ett `partyId`/UUID                                               |
| `PiiMasker.maskPersonalNumber(s)`  | Ett personnummer / `legalId`                                     |
| `PiiMasker.maskEmail(s)`           | En e-postadress                                                  |
| `PiiMasker.maskPhoneNumber(s)`     | Ett telefonnummer                                                |

Alla metoder är null-säkra (`null` in → `null` ut).

`maskPersonalNumber` (och `maskPii`) maskar både **10-siffriga** (`YYMMDD-NNNN`, `YYMMDDNNNN`) och
**12-siffriga** (`YYYYMMDDNNNN`, `YYYYMMDD-NNNN`) personnummer/`legalId` samt 10-siffriga
organisationsnummer. Kräver dept44 **8.0.9+** (12-siffrigt stöd tillkom där; 8.0.8 maskar endast
10-siffriga former).

### Är värdet både angriparstyrt OCH PII?

Kombinera – de skyddar mot olika saker:

```java
LOG.info("Tog emot {}", LogUtils.sanitizeForLogging(PiiMasker.maskPii(userInput)));
```

### Bästa vägen: logga inte identifieraren alls

Ofta behövs inte identifieraren i loggen. Logga en räknare, ett internt id eller en status i
stället för `partyId`/personnummer:

```java
LOG.info("Hittade {} poster utan partyId", records.size());   // ok – ingen PII
```

## Skyddsnät på ramverksnivå (ersätter inte maskning i koden)

`dept44-starter-logback-logserver` har en **opt-in** maskerare som maskar PII i *varje* loggrad
(konsol och GELF), oavsett om koden kom ihåg att maska:

```properties
dept44.logback.pii-masking.enabled=true
```

Före dept44 **8.1.0** maskades inte GELF, bara konsolen. GELF-layouterna var konfigurerade med
namn som logback-gelf 6 inte känner till, så encodern använde sina egna layouter utan maskering. En
tjänst med egen `logback-spring.xml` som har kopierat GELF-encodern måste byta `shortPatternLayout`
och `fullPatternLayout` mot `shortMessageLayout` och `fullMessageLayout`. Varningen
`Ignoring unknown property [shortPatternLayout]` vid start visar felet.

Detta är ett skyddsnät (defense-in-depth), **inte** en ersättning för att maska vid källan:

- Det maskar bara det renderade **meddelandet**. MDC-värden, caller data och stack traces som
  GELF-encodern skickar som separata fält maskas **inte**.
- Fri text (namn, adress) fångas inte av regex.
- En tjänst med egen `logback-spring.xml` måste själv replikera `<conversionRule>` och
  `%maskPii`-mönstret.

### Varje kategori kan stängas av för sig

Varje kategori maskas om inte dess egen property säger `false`, så att slå på maskningen maskar allt
som tidigare:

```properties
dept44.logback.pii-masking.uuid=false
dept44.logback.pii-masking.personal-number=false
dept44.logback.pii-masking.phone-number=false
dept44.logback.pii-masking.email=false
```

Den kategori som kräver ett beslut är **`uuid`**. Ett UUID maskas till sina fyra första tecken, och
fyra tecken räcker inte för att knyta en loggrad till ett ärende, en bilaga eller ett meddelande. En
tjänst som spårar en incident via `errandId`/`attachmentId` – eller som maskar sina HTTP-payloads
fält för fält och medvetet behåller identifierarna – förlorar det om varje UUID i varje loggrad
kortas av. Den tjänsten kan stänga av `uuid` och ändå få övriga kategorier maskade.

Med `uuid=false` loggas varje `partyId` i klartext, om inte koden har maskat det själv.
UUID-regeln kan inte skilja ett `partyId` från ett `errandId`, så skyddsnätet täcker det inte
längre i någon loggrad, payload-loggen inräknad. `partyId` är en direkt identifierare mot
Party-tjänsten och kan slås upp till personnummer, vilket är hela skälet till att UUID-maskning
finns. Att stänga av `uuid` är ett avsteg från kärnregeln överst i den här policyn och kräver ett
beslut, inte bara en ändring i konfigurationen. Maska `partyId` vid källan (`PiiMasker.maskUuid`,
eller en payload-maskning som inte behåller det) innan `uuid` stängs av.

Kräver dept44 **8.1.0+**; tidigare versioner har bara `enabled` och maskar alla kategorier.

Se `dept44-starter-logback-logserver/readme.md` för detaljer och begränsningar.

### Payload-loggen maskas med en vitlista

Från dept44 **8.1.0** maskar `dept44-starter` alla bodies i payload-loggen
(`se.sundsvall.dept44.payload`) med en vitlista som är påslagen som default. Det gäller inkommande
anrop och utgående anrop via Feign, WebClient och WebServiceTemplate. Bara värdena i de fält som
står på listan loggas. Alla andra värden i en JSON-body ersätts med `[masked]`, oavsett typ, och en
body som inte är JSON loggas utan body. Fältnamnen behålls, så att strukturen syns. Listan är tom
som default, så inga värden loggas förrän tjänsten själv namnger fälten:

```yaml
logbook:
  body-filters:
    allowlist:
      paths:
        - '$..errandId'
        - '$..status'
```

Vitlista bara fält som du vet inte innehåller persondata, till exempel ärende-id och statusar.
`partyId`, personnummer, namn, adresser och fritextfält hör inte hemma på listan. En path pekar på
ett fält med ett enskilt värde, så ett fält som läggs till i ett objekt senare loggas inte förrän
någon lägger till det på listan. Undantaget är en array av enskilda värden: en path till den loggar
alla värden i den, till exempel alla adresser i `$..recipients`.

Fältnamn loggas alltid, även när de är data. En map med personnummer eller e-postadresser som
nycklar loggas alltså med nycklarna i klartext, eftersom vitlistan inte kan skilja en sådan map från
ett objekt. Logback-maskeraren ovan maskar de kategorier den känner till var de än står i raden.

Med `logbook.body-filters.allowlist.enabled: false` loggas hela bodies, som före 8.1.0. Det är ett
avsteg från kärnregeln och passar bara en tjänst vars payloads inte innehåller persondata.

Logback-maskeraren körs även över payload-loggen. Med `uuid` påslaget kortas därför också
vitlistade UUID:n av, till exempel ett `errandId`. En tjänst som behöver dem i klartext kan stänga
av `uuid` enligt avsnittet ovan. Vitlistan ser då fortfarande till att ett `partyId` i en payload
inte loggas, så länge det inte står på listan. I övriga loggrader gäller fortfarande att koden själv
maskar det.

Se `dept44-starter/README.md` för syntaxen och fler inställningar.

## Checklista vid PR

- [ ] Loggar har granskats för PII – inga `partyId`, personnummer/`legalId`, namn, e-post eller
  telefonnummer i klartext.
- [ ] Där PII loggas är värdet maskat med `PiiMasker` (inte enbart `sanitizeForLogging`).
- [ ] Nya fält på vitlistan (`logbook.body-filters.allowlist.paths`) innehåller ingen persondata.

## Referenser

- `se.sundsvall.dept44.util.PiiMasker` (`dept44-starter`)
- `se.sundsvall.dept44.util.LogUtils#sanitizeForLogging` – *endast* log injection
- `dept44-starter-logback-logserver/readme.md` – ramverkets opt-in-maskerare
- `dept44-starter/README.md` – vitlistan för payload-loggen (`logbook.body-filters.allowlist`)

