

import java.math.BigInteger;
import java.security.SecureRandom;

public class Krypto {
    private BigInteger N;
    private BigInteger e;   // öffentlicher exponent
    private BigInteger d;   //privater exponent

    public Krypto (int bitLength) {
        System.out.println("Erzeuge " + bitLength + "-Bit-Schlüsselpaar");

        BigInteger big = new BigInteger("65537");
        this.e = big;

        SecureRandom rand = new SecureRandom();
        int primeBitLength = bitLength / 2;

        while (true) {
            BigInteger p = BigInteger.probablePrime(primeBitLength, rand);
            BigInteger q = BigInteger.probablePrime(primeBitLength, rand);

            if ( p.equals(q)) {
                continue;
            }

            this.N = p.multiply(q);

            BigInteger p_minus_1 = p.subtract(BigInteger.ONE);
            BigInteger q_minus_1 = q.subtract(BigInteger.ONE);
            BigInteger phi = p_minus_1.multiply(q_minus_1);

            // prüfe, ob e und phi teilerfremd sind (also ggT(e, phi) == 1)

            if (e.gcd(phi).equals(BigInteger.ONE)) {
                this.d = e.modInverse(phi);

                break;
            }

        }
        System.out.println("Schlüsselpaar erzeugt:");
    }

    public String getModuleHex () {
        return N.toString(16);
    }

    public String getPubkeyHex () {
        return e.toString(16);
    }

    public BigInteger decrypt (BigInteger cipherText) {
        return cipherText.modPow(d, N);
    }
}
