package certificate.wkdServer.src;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class GpgService {
    public byte [] getPublicKey(String email) {

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("gpg", "--export", email);

        try {
            Process process = processBuilder.start();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            InputStream input = process.getInputStream();

            byte[] data = new byte[1024];
            int nRead;
            while ((nRead = input.read(data, 0, data.length)) != -1) {
                output.write(data, 0, nRead);
        }

        int exitCode = process.waitFor();

        byte[] keyData = output.toByteArray();
        if (exitCode == 0 && keyData.length > 0) {
            return keyData;
        } else {
            return null;
        }
    } catch (IOException | InterruptedException e) {
        e.printStackTrace();
        return null;
    }
}
}
