# mail-util – E-Mail Helper Classes

A Java library for constructing and manipulating MIME-formatted e-mail via a custom XML format. Used at [Bruun Rasmussen Kunstauktioner](https://bruun-rasmussen.dk) for transactional and notification e-mails.

## Features

- **XML-to-MIME** – define e-mails in a concise XML schema and compile them into standards-compliant MIME messages, with automatic embedding of images and stylesheets as related MIME parts.
- **MIME-to-XML** – marshal existing `MimeMessage` objects back into the XML format for storage or re-processing.
- **Inky templating** – optional [ZURB Foundation for Emails](https://get.foundation/emails.html) (Inky) support, transforming responsive markup into table-based HTML via XSLT.
- **CSS inlining** – inline CSS rules into element `style` attributes for maximum e-mail client compatibility.
- **Link tagging** – automatically append tracking query parameters (e.g. UTM tags) to URLs matching configured domain patterns.

## XML format

E-mails are described in an `<email>` element (or batched in `<email-list>`):

```xml
<email>
  <addresses>
    <from>
      <personal>Acme Support</personal>
      <email-address>support@acme.example</email-address>
    </from>
    <to>
      <personal>Jane Doe</personal>
      <email-address>jane@example.com</email-address>
    </to>
  </addresses>
  <subject>Your order confirmation</subject>
  <header name="X-Order-Id" value="12345"/>
  <plain-body>Thanks for your order!</plain-body>
  <html-body>
    <html>
      <body>
        <p>Thanks for your order!</p>
        <img src="https://cdn.example.com/logo.png"/>
      </body>
    </html>
  </html-body>
</email>
```

Images referenced in `<html-body>` are fetched and embedded as related MIME parts automatically (the `src` attribute is rewritten to a `cid:` reference).

## Usage

```java
// Parse XML into mail data objects:
Document doc = /* your XML document */;
MailMessageData[] mails = MailMessageParser.parseMails(doc.getDocumentElement());

// Compose a javax.mail MimeMessage:
Session session = Session.getInstance(new Properties());
MimeMessage mime = mails[0].compose(session, true);

// Marshal a MimeMessage back to XML:
Document xml = MailMessageMarshaller.marshal(mimeMessage);
```

## Build

Requires Java 8+ and Maven.

```bash
mvn compile        # compile sources
mvn test           # run tests
mvn package        # build jar
```

## Configuration

| Property / env var | Purpose | Default |
|---|---|---|
| `MAIL_PARSER_CONFIG` (env) | URL or classpath path to tracking config properties | `dk/br/mail/mail-parser-config.properties` |
| `dk.br.mail.html-encoding` (system) | HTML output encoding | `UTF-8` |
| `dk.br.mail.inline-css` (system) | Enable CSS inlining (`true`/`1`/`yes`) | `false` |
| `inky.outline-css` (system) | Override Inky responsive/outline CSS resource | built-in `email.css` |
| `inky.styling-css` (system) | Override Inky inline styling CSS resource | built-in `email-inlined.css` |

## Dependencies

- **JavaMail** (`javax.mail`) – `provided` scope; consumers must supply an implementation
- **JSR-107 Cache API** – `javax.cache`; a compliant implementation (e.g. Ehcache 3) is needed at runtime
- **Jsoup** – HTML parsing
- **jStyleParser** – CSS parsing and inlining
- **Commons Lang / Codec / IO** – utilities

## License

[MIT](LICENSE)
