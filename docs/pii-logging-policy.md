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

| Kategori | Exempel | Maska? |
|---|---|---|
| `partyId` (UUID) | `f47ac10b-58cc-4372-a567-0e02b2c3d479` | **Ja** |
| Personnummer / samordningsnummer / `legalId` | `900101-1234` | **Ja** |
| E-postadress | `john.doe@example.com` | **Ja** |
| Telefonnummer | `070-123 45 67` | **Ja** |
| Namn, adress | `Anna Andersson`, `Storgatan 1` | **Ja** – maska vid källan (regex fångar inte fri text) |
| Organisationsnummer | `5560000000` | Bedöm i sammanhanget – maska vid tveksamhet |

## Så här gör du

Använd `PiiMasker` från `dept44-starter`. En generell ingång maskar alla kategorier i ett svep:

```java
import se.sundsvall.dept44.util.PiiMasker;

LOG.info("Inga kontaktinställningar hittades för {} med filter {}",
    PiiMasker.maskPii(partyId), filters);
// → "... för f47a... med filter {...}"
```

För ett värde vars typ är känd kan du använda en riktad metod – tydligare och billigare:

| Metod | Använd för |
|---|---|
| `PiiMasker.maskPii(s)` | Generellt – godtycklig sträng som kan innehålla flera kategorier |
| `PiiMasker.maskUuid(s)` | Ett `partyId`/UUID |
| `PiiMasker.maskPersonalNumber(s)` | Ett personnummer / `legalId` |
| `PiiMasker.maskEmail(s)` | En e-postadress |
| `PiiMasker.maskPhoneNumber(s)` | Ett telefonnummer |

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

Detta är ett skyddsnät (defense-in-depth), **inte** en ersättning för att maska vid källan:

- Det maskar bara det renderade **meddelandet**. MDC-värden, caller data och stack traces som
  GELF-encodern skickar som separata fält maskas **inte**.
- Fri text (namn, adress) fångas inte av regex.
- En tjänst med egen `logback-spring.xml` måste själv replikera `<conversionRule>` och
  `%maskPii`-mönstret.

Se `dept44-starter-logback-logserver/readme.md` för detaljer och begränsningar.

## Checklista vid PR

- [ ] Loggar har granskats för PII – inga `partyId`, personnummer/`legalId`, namn, e-post eller
      telefonnummer i klartext.
- [ ] Där PII loggas är värdet maskat med `PiiMasker` (inte enbart `sanitizeForLogging`).

## Referenser

- `se.sundsvall.dept44.util.PiiMasker` (`dept44-starter`)
- `se.sundsvall.dept44.util.LogUtils#sanitizeForLogging` – *endast* log injection
- `dept44-starter-logback-logserver/readme.md` – ramverkets opt-in-maskerare
