package io.github.bobistuca.vertex.runtime;

import java.io.IOException;
import java.io.InputStream;

/**
 * Loads classes that are embedded in Vertex.jar as {@code embedded/<group>/<path>.class.bin} resources.
 *
 * <p>Shading relocates {@code net.kyori} for the libraries bundled with Vertex. Adapter code that must talk to the
 * server's own (native) Adventure classes is therefore kept out of the relocated class set and loaded here. Class
 * resolution still delegates to the parent first, so the adapter sees Vertex's core classes and the server API.
 */
public final class EmbeddedClassLoader extends ClassLoader {
    private final String group;
    private final String packagePrefix;

    public EmbeddedClassLoader(ClassLoader parent, String group, String packagePrefix) {
        super(parent);
        this.group = group;
        this.packagePrefix = packagePrefix;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (!name.startsWith(packagePrefix)) {
            throw new ClassNotFoundException(name);
        }
        String resource = "embedded/" + group + "/" + name.replace('.', '/') + ".class.bin";
        try (InputStream in = getParent().getResourceAsStream(resource)) {
            if (in == null) {
                throw new ClassNotFoundException(name);
            }
            byte[] bytes = in.readAllBytes();
            return defineClass(name, bytes, 0, bytes.length);
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
    }
}
