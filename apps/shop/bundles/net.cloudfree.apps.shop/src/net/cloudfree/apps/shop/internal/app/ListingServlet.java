/*******************************************************************************
 * Copyright (c) 2008, 2010 Gunnar Wagenknecht and others.
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Contributors:
 *     Gunnar Wagenknecht - initial API and implementation
 *******************************************************************************/
package net.cloudfree.apps.shop.internal.app;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.eclipse.gyrex.cds.model.IListingManager;
import org.eclipse.gyrex.cds.model.documents.Document;
import org.eclipse.gyrex.cds.model.solr.ISolrQueryExecutor;
import org.eclipse.gyrex.context.IRuntimeContext;
import org.eclipse.gyrex.model.common.ModelUtil;

public class ListingServlet extends HttpServlet {

	static class FacetExpressionFilter extends FacetFilter {

		private final String[] expressions;

		FacetExpressionFilter(final String name, final String... expressions) {
			super(name);
			this.expressions = expressions;
		}

		@Override
		boolean addFilter(final SolrQuery query, final String value) {
			for (final String expression : expressions) {
				if (expression.equals(value)) {
					query.addFilterQuery(name + ":" + expression);
					return true;
				}
			}
			return false;
		}

		@Override
		void defineFilter(final SolrQuery query) {
			for (final String expression : expressions) {
				query.addFacetQuery(name + ":" + expression);
			}
		}
	}

	static class FacetFilter {
		String name;

		FacetFilter(final String name) {
			this.name = name;
		}

		boolean addFilter(final SolrQuery query, final String value) {
			query.addFilterQuery(name + ":" + value);
			return true;
		}

		void defineFilter(final SolrQuery query) {
			query.addFacetField(name);
		}
	}

	/** serialVersionUID */
	private static final long serialVersionUID = 1L;

	private final IRuntimeContext context;
	private final Map<String, FacetFilter> facetFilters = new HashMap<String, FacetFilter>();

	/**
	 * Creates a new instance.
	 * 
	 * @param context
	 */
	public ListingServlet(final IRuntimeContext context) {
		this.context = context;

		// initialize facet filters
		facetFilters.put("style_n", new FacetFilter("style_n"));
		facetFilters.put("color_n", new FacetFilter("color_n"));
		facetFilters.put("source_n", new FacetFilter("source_n"));
		facetFilters.put("size_n", new FacetFilter("size_n"));
		facetFilters.put("category", new FacetFilter("category"));
		facetFilters.put("thickness", new FacetFilter("thickness"));
		facetFilters.put("fit", new FacetFilter("fit"));
		facetFilters.put("paper", new FacetFilter("paper"));
		facetFilters.put("finish", new FacetFilter("finish"));
		facetFilters.put("price", new FacetExpressionFilter("price", "[* TO 10]", "[10 TO 20]", "[20 TO 30]", "[30 TO 50]", "[50 TO *]"));
	}

	@Override
	protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		final IListingManager manager = ModelUtil.getManager(IListingManager.class, getContext());

		final ISolrQueryExecutor queryExecutor = (ISolrQueryExecutor) manager.getAdapter(ISolrQueryExecutor.class);
		if (null == queryExecutor) {
			resp.sendError(404);
			return;
		}

		final List<String> selectedFacets = new ArrayList<String>();

		final SolrQuery query = new SolrQuery();
		boolean facet = true;
		boolean checkVariations = false;

		final String path = req.getPathInfo();
		if ((null != path) && (path.length() > 1)) {
			query.setFilterQueries(Document.URI_PATH + ":" + path.substring(1));
			query.setFields("id", "title", "price", "name", "score", "img480", "uripath", "description");
			facet = false;
			checkVariations = true;
		} else {
			final String q = req.getParameter("q");
			if (StringUtils.isNotBlank(q)) {
				query.setQuery(q);
			}

			query.setFields("id", "title", "price", "name", "score", "img48", "uripath");

			// ignore variations
			final String f = req.getParameter("f");
			if (StringUtils.isNotBlank(f)) {
				query.addFilterQuery(f);
			} else {
				query.addFilterQuery("-type:variation");
			}

			// facet narrowing?
			final String[] narrows = req.getParameterValues("narrow");
			if (null != narrows) {
				for (final String narrow : narrows) {
					if (StringUtils.isBlank(narrow)) {
						continue;
					}
					final String[] split = StringUtils.split(narrow, ':');
					if (split.length != 2) {
						continue;
					}
					final String name = split[0];
					final String value = split[1];
					if (StringUtils.isBlank(name) || StringUtils.isBlank(value)) {
						continue;
					}
					final FacetFilter filter = facetFilters.get(name);
					if (null != filter) {
						if (filter.addFilter(query, value)) {
							selectedFacets.add(name + ":" + value);
						}
					}
				}
			}
		}

		query.setQueryType("dismax");
		query.set("debug", true);

		// facet fields
		if (facet) {
			query.setFacet(true);
			for (final FacetFilter filter : facetFilters.values()) {
				filter.defineFilter(query);
			}
		}

		final QueryResponse response = queryExecutor.query(query);
		final SolrDocumentList results = response.getResults();

		resp.setContentType("text/html");
		resp.setCharacterEncoding("UTF-8");

		final PrintWriter writer = resp.getWriter();

		writer.println("<html><head>");
		writer.println("<title>");
		writer.println(path);
		writer.println(" - Shop Listings</title>");
		writer.println("</head><body>");

		writer.println("<h1>Found Listings</h1>");
		writer.println("<p>");
		writer.println("CloudFree found <strong>" + results.getNumFound() + "</strong> products in " + (response.getQTime() < 1000 ? "less than a second." : (response.getQTime() + "ms.")));
		if (results.size() < results.getNumFound()) {
			writer.println("<br/>");
			if (results.getStart() == 0) {
				writer.println("Only the first " + results.size() + " products are shown.");
			} else {
				writer.println("Only products " + results.getStart() + " till " + (results.getStart() + results.size()) + " will be shown.");
			}
		}
		writer.println("</p>");

		final List<FacetField> facetFields = response.getFacetFields();
		if ((null != facetFields) && !facetFields.isEmpty()) {
			writer.println("<p>");
			writer.println("You can filter the results by: </br>");
			for (final FacetField facetField : facetFields) {
				final List<Count> values = facetField.getValues();
				if ((null != values) && !values.isEmpty()) {
					writer.println("<div style=\"float:left;\">");
					writer.print("<em>");
					writer.print(facetField.getName());
					writer.print("</em>");
					writer.println("<ul style=\"margin:0;\">");
					int filters = 0;
					for (final Count count : values) {
						if (count.getCount() == 0) {
							continue;
						}
						writer.print("<li>");
						writer.print(count.getName());
						writer.print(" (");
						writer.print(count.getCount());
						writer.print(")");
						writer.print("</li>");
						filters++;
					}
					if (filters == 0) {
						writer.print("<li>none</li>");
					}
					writer.println("</ul>");
					writer.println("</div>");
				}
			}
			writer.println("<div style=\"clear:both;\">&nbsp;</div>");
			writer.println("</p>");
		}

		writer.println("<p>");
		if (!results.isEmpty()) {
			for (final SolrDocument listing : results) {
				writeListing(listing, writer, req);

				if (checkVariations) {
					final SolrQuery query2 = new SolrQuery();
					query2.setQuery("parentid:" + listing.getFirstValue("id"));
					query2.setFields("id", "title", "price", "name", "score", "img48", "uripath", "color", "size");
					final QueryResponse response2 = queryExecutor.query(query2);
					final SolrDocumentList results2 = response2.getResults();
					if ((null != results2) && !results2.isEmpty()) {
						writer.println("There are " + results2.size() + " variations available.");
						for (final SolrDocument variation : results2) {
							writeListing(variation, writer, req);
						}
					}
				}
			}
		} else {
			writer.println("No listings found!");
		}
		writer.println("</p>");
		writer.println("</body>");

	}

	private StringBuilder getBaseUrl(final HttpServletRequest req) {
		final StringBuilder builder = new StringBuilder(50);
		builder.append(req.getScheme());
		builder.append("://");
		builder.append(req.getServerName());
		if ((req.getScheme().equals("http") && (req.getServerPort() != 80)) || (req.getScheme().equals("https") && (req.getServerPort() != 443))) {
			builder.append(":");
			builder.append(req.getServerPort());
		}
		builder.append(req.getContextPath());
		builder.append(req.getServletPath());
		builder.append("/");
		return builder;
	}

	/**
	 * Returns the context.
	 * 
	 * @return the context
	 */
	public IRuntimeContext getContext() {
		return context;
	}

	private void writeListing(final SolrDocument listing, final PrintWriter writer, final HttpServletRequest req) {
		writer.println("<div style=\"float:left;\">");
		final Object uripath = listing.getFirstValue("uripath");
		if (null != uripath) {
			writer.print("<a href=\"");
			writer.print(getBaseUrl(req).append(uripath));
			writer.print("\">");
		}
		writer.print("<img border=\"0\" src=\"");
		Object thumb = listing.getFirstValue("img48");
		if (null == thumb) {
			thumb = listing.getFirstValue("img480");
		}
		writer.print(thumb);
		writer.print("\">");
		if (null != uripath) {
			writer.print("</a>");
		}
		writer.println("</div>");
		writer.println("<br/>");
		writer.print("<strong>");
		writer.print(listing.getFirstValue("title"));
		writer.print("</strong>");
		writer.println("<br/>");
		writer.print("<small>");
		writer.print(listing.getFirstValue("score"));
		writer.println("<br/>");
		final Object size = listing.getFirstValue("size");
		if (null != size) {
			writer.print("Size: ");
			writer.print(size);
			writer.println("<br/>");
		}
		final Object color = listing.getFirstValue("color");
		if (null != color) {
			writer.print("Color: ");
			writer.print(color);
			writer.println("<br/>");
		}
		writer.print("</small>");
		writer.print("<span style=\"font-size: 2em;\">");
		NumberFormat.getCurrencyInstance(Locale.GERMAN).format((Double) listing.getFirstValue("price"));
		writer.print("</span><br/>");
		final Object desc = listing.getFirstValue("description");
		if (null != desc) {
			writer.println("<br/>");
			writer.println("<blockquote>");
			writer.print(desc);
			writer.println("</blockquote>");
		}

		//		writer.print("<pre>");
		//		writer.println(listing.toString());
		//		writer.print("</pre>");
		writer.println("<div style=\"clear:both;\">&nbsp;</div>");
	}
}
