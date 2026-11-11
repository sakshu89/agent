package com.ultron.core.agent;
import com.ultron.core.Observation; import java.util.List;
/** Common OCR/accessibility/UIA/AT-SPI screen representation for all platform adapters. */
@FunctionalInterface public interface ScreenUnderstandingProvider {
 ScreenSnapshot understand(Observation observation)throws Exception;
 record Element(String role,String text,String description,String bounds,boolean actionable){public Element{role=n(role);text=n(text);description=n(description);bounds=n(bounds);}private static String n(String s){return s==null?"":s;}}
 record ScreenSnapshot(String platform,String summary,List<Element> elements){public ScreenSnapshot{platform=platform==null?"unknown":platform;summary=summary==null?"":summary;elements=elements==null?List.of():List.copyOf(elements);}}
}
