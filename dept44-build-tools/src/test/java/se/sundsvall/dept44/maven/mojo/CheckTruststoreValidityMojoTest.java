package se.sundsvall.dept44.maven.mojo;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.x500.AttributeTypeAndValue;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.bc.BcX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CheckTruststoreValidityMojoTest {

    private static final Provider BC_PROVIDER = new BouncyCastleProvider();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final String TEST_BASE_DIR = "target/test-certs";

    @Mock
    private MavenProject mockMavenProject;

    private CheckTruststoreValidityMojo mojo;

    @BeforeEach
    void setUp() throws Exception {
        mojo = new CheckTruststoreValidityMojo();
        mojo.setProject(mockMavenProject);
    }

    @Test
    void executeWhenSkipAllChecksIsSet() {
        mojo.setSkipAllChecks(true);

        assertThatNoException().isThrownBy(mojo::execute);
    }

    @Test
    void executeWhenSkipIsSet() {
        mojo.setSkip(true);

        assertThatNoException().isThrownBy(mojo::execute);
    }

    @Test
    void executeWithExpiringCert() throws Exception {
        var truststorePath = "truststore";

        createSelfSignedCert(LocalDate.now().plusDays(1), truststorePath);

        when(mockMavenProject.getBasedir()).thenReturn(new File(TEST_BASE_DIR));

        mojo.setTruststorePath(truststorePath);
        mojo.setMonthsUntilExpiration(1);

        assertThatExceptionOfType(MojoFailureException.class)
            .isThrownBy(mojo::execute)
            .withMessageContaining("Certificate 'test.cer' expiration date");
    }

    @Test
    void executeWithValidCert() throws Exception {
        var truststorePath = "truststore";
        createSelfSignedCert(LocalDate.now().plusYears(1), truststorePath);

        when(mockMavenProject.getBasedir()).thenReturn(new File(TEST_BASE_DIR));

        mojo.setTruststorePath(truststorePath);
        mojo.setMonthsUntilExpiration(1);

        assertThatNoException().isThrownBy(mojo::execute);
    }

    /**
     * Generates a self-signed certificate, valid to the provided date and
     *
     * @param notAfter the last date the certificate should be considered valid to.
     * @param truststorePath where to store the generated certificate
     * @throws Exception on errors.
     */
    private static void createSelfSignedCert(final LocalDate notAfter, final String truststorePath) throws Exception {
        var serial = new BigInteger(Long.SIZE, SECURE_RANDOM);
        var issuerAndSubject = createX500Name();
        var keyPair = generateKeyPair();
        var publicKey = keyPair.getPublic();
        var encodedPublicKey = publicKey.getEncoded();
        var publicKeyInfo = SubjectPublicKeyInfo.getInstance(encodedPublicKey);

        try (var bais = new ByteArrayInputStream(encodedPublicKey); var ais = new ASN1InputStream(bais)) {
            var asn1Sequence = (ASN1Sequence) ais.readObject();
            var subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(asn1Sequence);
            var subjectPublicKeyId = new BcX509ExtensionUtils().createSubjectKeyIdentifier(subjectPublicKeyInfo);

            var certBuilder = new X509v3CertificateBuilder(issuerAndSubject, serial,
                Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()),
                Date.from(notAfter.atStartOfDay(ZoneId.systemDefault()).toInstant()),
                issuerAndSubject, publicKeyInfo);
            var contentSigner = new JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.getPrivate());
            var certHolder = certBuilder
                .addExtension(Extension.basicConstraints, true, new BasicConstraints(true))
                .addExtension(Extension.subjectKeyIdentifier, false, subjectPublicKeyId)
                .build(contentSigner);

            var cert = new JcaX509CertificateConverter().setProvider(BC_PROVIDER).getCertificate(certHolder);

            var outDir = new File(TEST_BASE_DIR, "/src/main/resources/" + truststorePath);
            if (!outDir.exists()) {
                outDir.mkdirs();
            }

            var writer = new JcaPEMWriter(new FileWriter(new File(outDir, "test.cer")));
            writer.writeObject(cert);
            writer.flush();
        }
    }

    /**
     * Generates a key pair.
     *
     * @return a key pair.
     * @throws Exception on failure.
     */
    private static KeyPair generateKeyPair() throws Exception {
        var keyPairGenerator = KeyPairGenerator.getInstance("RSA", BC_PROVIDER);
        keyPairGenerator.initialize(1024, SECURE_RANDOM);
        return keyPairGenerator.generateKeyPair();
    }

    /**
     * Generates an X500 name.
     *
     * @return an X500 name.
     */
    private static X500Name createX500Name() {

        return new X500Name(new RDN[] {
            new RDN(new AttributeTypeAndValue[] {
                new AttributeTypeAndValue(BCStyle.CN, new DERUTF8String("CN")),
                new AttributeTypeAndValue(BCStyle.OU, new DERUTF8String("OU")),
                new AttributeTypeAndValue(BCStyle.O, new DERUTF8String("O")),
                new AttributeTypeAndValue(BCStyle.L, new DERUTF8String("L")),
                new AttributeTypeAndValue(BCStyle.ST, new DERUTF8String("ST")),
                new AttributeTypeAndValue(BCStyle.C, new DERUTF8String("SE"))
            })
        });
    }
}
