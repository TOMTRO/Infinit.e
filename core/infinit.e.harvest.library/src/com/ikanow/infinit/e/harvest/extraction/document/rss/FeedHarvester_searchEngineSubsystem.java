/*******************************************************************************
 * Copyright 2012, The Infinit.e Open Source Project.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package com.ikanow.infinit.e.harvest.extraction.document.rss;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;

import com.ikanow.infinit.e.data_model.store.config.source.SourcePojo;
import com.ikanow.infinit.e.data_model.store.config.source.SourceRssConfigPojo;
import com.ikanow.infinit.e.data_model.store.config.source.SourceRssConfigPojo.ExtraUrlPojo;
import com.ikanow.infinit.e.data_model.store.config.source.SourceSearchFeedConfigPojo;
import com.ikanow.infinit.e.data_model.store.config.source.UnstructuredAnalysisConfigPojo;
import com.ikanow.infinit.e.data_model.store.config.source.UnstructuredAnalysisConfigPojo.Context;
import com.ikanow.infinit.e.data_model.store.document.DocumentPojo;
import com.ikanow.infinit.e.harvest.HarvestContext;
import com.ikanow.infinit.e.harvest.enrichment.custom.UnstructuredAnalysisHarvester;

public class FeedHarvester_searchEngineSubsystem {

	public void generateFeedFromSearch(SourcePojo src, HarvestContext context) {
		
		String savedUrl = src.getUrl();
		SourceRssConfigPojo feedConfig = src.getRssConfig();		
		SourceSearchFeedConfigPojo searchConfig = feedConfig.getSearchConfig();
		if ((null == feedConfig) || (null == searchConfig)) {
			return;
		}
		
		UnstructuredAnalysisConfigPojo savedUAHconfig = src.getUnstructuredAnalysisConfig(); // (can be null)
		String savedUserAgent = feedConfig.getUserAgent(); 

		
		try { // If we error out, we're probably going to abandon the entire search
			
		// We're going to loop over pages
		
		// Apply the regex to the URL for pagination, part 1
			
			int nMaxPages = 1;
			Pattern pageChangeRegex = null;
			Matcher pageChangeRegexMatcher = null;
			if (null != feedConfig.getSearchConfig().getPageChangeRegex()) {
				pageChangeRegex = Pattern.compile(feedConfig.getSearchConfig().getPageChangeRegex(), Pattern.CASE_INSENSITIVE);
				pageChangeRegexMatcher = pageChangeRegex.matcher(savedUrl);
				nMaxPages = feedConfig.getSearchConfig().getNumPages();
			}//TOTEST

			for (int nPage = 0; nPage < nMaxPages; ++nPage) {
				String url = savedUrl;	
				
		// Apply the regex to the URL for pagination, part 2
				
				if ((null != pageChangeRegex) && (null != feedConfig.getSearchConfig().getPageChangeReplace())) {
					int nResultStart = nPage*feedConfig.getSearchConfig().getNumResultsPerPage();
					String replace = feedConfig.getSearchConfig().getPageChangeReplace().replace("$1", Integer.toString(nResultStart));

					if (null == pageChangeRegexMatcher) {
						url += replace;
					}
					else {
						url = pageChangeRegexMatcher.replaceFirst(replace);
					}
				}//TOTEST
				
		// Create a custom UAH object to fetch and parse the search results
		
				UnstructuredAnalysisConfigPojo dummyUAHconfig = new UnstructuredAnalysisConfigPojo();
				dummyUAHconfig.AddMetaField("searchEngineSubsystem", Context.First, feedConfig.getSearchConfig().getScript(), "javascript");
				src.setUnstructuredAnalysisConfig(dummyUAHconfig);
				if (null != searchConfig.getUserAgent()) {
					feedConfig.setUserAgent(searchConfig.getUserAgent());
				}
				
				DocumentPojo searchDoc = new DocumentPojo();
				searchDoc.setUrl(url);
				//TODO (INF-1282): any other fields needed?
				
				UnstructuredAnalysisHarvester dummyUAH = new UnstructuredAnalysisHarvester();
				boolean bMoreDocs = (nPage < nMaxPages - 1);
				dummyUAH.executeHarvest(context, src, searchDoc, bMoreDocs);
				
		// Create extraUrl entries from the metadata
		
				Object[] searchResults = searchDoc.getMetaData().get("searchEngineSubsystem");
				if (null != searchResults) {
					for (Object searchResultObj: searchResults) {
						try {
							JSONObject jsonObj = (JSONObject)searchResultObj;
							// 3 fields: url, title, description(=optional)
							String linkUrl = jsonObj.getString("url");
							String linkTitle = jsonObj.getString("title");
							String linkDesc = jsonObj.getString("description");
							
							if ((null != linkUrl) && (null != linkTitle)) {
								SourceRssConfigPojo.ExtraUrlPojo link = new SourceRssConfigPojo.ExtraUrlPojo();
								link.url = linkUrl;
								link.title = linkTitle;
								link.description = linkDesc;
								if (null == feedConfig.getExtraUrls()) {
									feedConfig.setExtraUrls(new ArrayList<ExtraUrlPojo>(searchResults.length));
								}
								feedConfig.getExtraUrls().add(link);
							}
						}
						catch (Exception e) {
							// (just carry on)
						}
					}
				}//TOTEST			
			
			}// end loop over pages
			
		}
		catch (Exception e) {
			//TODO (INF-1282): handle the exception
		}
		finally {
			// Fix any temp changes we made to the source
			src.setUnstructuredAnalysisConfig(savedUAHconfig);
			feedConfig.setUserAgent(savedUserAgent);
		}
			
	}//TOTEST
}
