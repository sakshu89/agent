package com.ultron.core.agent;
import java.net.URI; import java.util.List;
/** Shared research boundary; platform-specific providers supply search/read implementations. */
@FunctionalInterface public interface ResearchProvider {
 ResearchReport research(String query,int maxSources)throws Exception;
 record Source(String title,URI uri,String excerpt,String publisher){public Source{title=n(title);excerpt=n(excerpt);publisher=n(publisher);}private static String n(String s){return s==null?"":s;}}
 record ResearchReport(String query,List<Source> sources,String limitations){public ResearchReport{query=query==null?"":query;sources=sources==null?List.of():List.copyOf(sources);limitations=limitations==null?"":limitations;}}
}
