package cache;

import cache.entities.AnimeMappingXml;

import java.util.Optional;

public interface IAnimeMappingRepository {
    Optional<AnimeMappingXml> getAnimeMappingXml(String url);

    boolean saveAnimeMappingXml(AnimeMappingXml animeMappingXml);
}
