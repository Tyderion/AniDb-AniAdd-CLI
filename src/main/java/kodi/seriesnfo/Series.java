package kodi.seriesnfo;

import kodi.common.UniqueId;
import lombok.Builder;
import lombok.Value;
import lombok.val;

@Builder
@Value
public class Series {
    String title;
    String originalTitle;
}
