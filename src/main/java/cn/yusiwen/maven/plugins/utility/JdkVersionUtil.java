package cn.yusiwen.maven.plugins.utility;

import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class JdkVersionUtil {

    // JDK版本与主版本号映射表
    private static final Map<Integer, String> VERSION_MAP = new HashMap<>();

    static {
        VERSION_MAP.put(52, "JDK 8");
        VERSION_MAP.put(53, "JDK 9");
        VERSION_MAP.put(54, "JDK 10");
        VERSION_MAP.put(55, "JDK 11");
        VERSION_MAP.put(56, "JDK 12");
        VERSION_MAP.put(57, "JDK 13");
        VERSION_MAP.put(58, "JDK 14");
        VERSION_MAP.put(59, "JDK 15");
        VERSION_MAP.put(60, "JDK 16");
        VERSION_MAP.put(61, "JDK 17");
        VERSION_MAP.put(62, "JDK 18");
        VERSION_MAP.put(63, "JDK 19");
        VERSION_MAP.put(64, "JDK 20");
        VERSION_MAP.put(65, "JDK 21");
    }

    private JdkVersionUtil() {
    }

    public static String getMinimumJDKVersion(Path jarPath) {
        int maxMajorVersion = 0;
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;

                try (InputStream is = jarFile.getInputStream(entry);
                     DataInputStream dis = new DataInputStream(is)) {
                    // 跳过魔数（4字节）和次版本号（2字节）
                    dis.readInt();
                    dis.readShort();
                    int majorVersion = dis.readShort();
                    if (majorVersion > maxMajorVersion) {
                        maxMajorVersion = majorVersion;
                    }
                }
            }
        } catch (Exception e) {
            return "N/A";
        }
        return VERSION_MAP.getOrDefault(maxMajorVersion, "Unknown version: " + maxMajorVersion);
    }
}
