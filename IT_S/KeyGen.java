package IT_S;

import java.math.BigInteger;
import java.util.Random;

public class KeyGen {

    public static void main(String[] args) {
        KeyGen keyGen = new KeyGen();
        keyGen.generateKeys();
        System.out.println(keyGen.gettN().toString(16));
        System.out.println(keyGen.getE().toString(16));
        System.out.println(keyGen.getD().toString(16));
    }

    private BigInteger p;
    private BigInteger q;
    private BigInteger n;        //öffentlicher Modulus
    private BigInteger phi;
    private BigInteger e;        //öffentlicher Exponent
    private BigInteger d;       //privater Exponent

    private final int BIT_LENGTH = 512;

    public void generateKeys() {
        Random rand = new Random();

        p = BigInteger.probablePrime(BIT_LENGTH, rand);
        q = BigInteger.probablePrime(BIT_LENGTH, rand);
        n = p.multiply(q);
        phi = p.subtract(BigInteger.ONE).multiply(q.subtract(BigInteger.ONE));
        e = new BigInteger("65537");

        while (phi.gcd(e).compareTo(BigInteger.ONE) > 0 && e.compareTo(phi) < 0) {
            e = e.add(new BigInteger("2"));
        }
        d = e.modInverse(phi);
    }

    public BigInteger gettN() { return n; }
    public BigInteger getE() { return e; }
    public BigInteger getD() { return d; }
}