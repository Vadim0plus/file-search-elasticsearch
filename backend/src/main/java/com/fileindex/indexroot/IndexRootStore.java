package com.fileindex.indexroot;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class IndexRootStore {

    private final Map<String, IndexRoot> roots = new ConcurrentHashMap<>();

    public IndexRoot create(Path path) {
        String id = UUID.randomUUID().toString();
        IndexRoot root = new IndexRoot(id, path);
        roots.put(id, root);
        return root;
    }

    public Optional<IndexRoot> find(String id) {
        return Optional.ofNullable(roots.get(id));
    }

    public Collection<IndexRoot> findAll() {
        return roots.values();
    }

    public boolean existsForPath(Path path) {
        return roots.values().stream().anyMatch(r -> r.getPath().equals(path));
    }

    public void remove(String id) {
        roots.remove(id);
    }
}
