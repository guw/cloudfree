/*******************************************************************************
 * Copyright (c) 2008,2010 Gunnar Wagenknecht and others.
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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.gyrex.cds.model.IListing;
import org.eclipse.gyrex.cds.model.IListingAttribute;
import org.eclipse.gyrex.cds.model.documents.Document;
import org.eclipse.gyrex.cds.service.IListingService;
import org.eclipse.gyrex.cds.service.query.ListingQuery;
import org.eclipse.gyrex.cds.service.query.ListingQuery.ResultDimension;
import org.eclipse.gyrex.cds.service.query.ListingQuery.SortDirection;
import org.eclipse.gyrex.cds.service.result.IListingResult;
import org.eclipse.gyrex.cds.service.result.IListingResultFacet;
import org.eclipse.gyrex.cds.service.result.IListingResultFacetValue;
import org.eclipse.gyrex.context.IRuntimeContext;
import org.eclipse.gyrex.http.application.ApplicationException;
import org.eclipse.gyrex.services.common.ServiceUtil;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.ObjectMapper;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;

import com.ibm.icu.text.MeasureFormat;
import com.ibm.icu.util.CurrencyAmount;
import com.ibm.icu.util.ULocale;

public class JsonListingServlet extends HttpServlet {

	static interface Enhancer {
		void enhanceWithinObject(JsonGenerator json) throws IOException;
	}

	private static final String ID_PATH_PREFIX = "/_id/";

	/** serialVersionUID */
	private static final long serialVersionUID = 1L;

	private static StringBuilder getBaseUrl(final HttpServletRequest req) {
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

	private final IRuntimeContext context;

	/**
	 * Creates a new instance.
	 * 
	 * @param context
	 */
	public JsonListingServlet(final IRuntimeContext context) {
		this.context = context;
	}

	@Override
	protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		if (req.getParameter("help") != null) {
			doHelp(req, resp);
			return;
		}

		final IListingService listingService = ServiceUtil.getService(IListingService.class, getContext());
		final ListingQuery query = new ListingQuery();
		boolean isSingleListing = false;

		final String path = req.getPathInfo();
		if ((null != path) && (path.length() > 1)) {
			if (path.startsWith(ID_PATH_PREFIX)) {
				// ID path
				query.setFilterQueries(Document.ID + ":" + path.substring(ID_PATH_PREFIX.length()));
			} else {
				// URI path
				query.setFilterQueries(Document.URI_PATH + ":" + ListingQuery.escapeQueryChars(path.substring(1)));
			}
			query.setResultDimension(ResultDimension.FULL);
			query.setMaxResults(1);
			isSingleListing = true;
		} else {
			final String q = req.getParameter("q");
			if (StringUtils.isNotBlank(q)) {
				query.setQuery(q);
			}

			// ignore variations
			query.addFilterQuery("-type:variation");

			// add filters
			final String[] f = req.getParameterValues("f");
			if ((null != f) && (f.length > 0)) {
				for (final String filter : f) {
					if (StringUtils.isNotBlank(filter)) {
						query.addFilterQuery(filter);
					}
				}
			}

			// simple category selection
			final String[] categories = req.getParameterValues("c");
			if ((null != categories) && (categories.length > 0)) {
				for (final String cat : categories) {
					if (StringUtils.isNotBlank(cat)) {
						query.addFilterQuery("+category:" + ListingQuery.escapeQueryChars(cat));
					}
				}
			}

			// simple tags selection
			final String[] tags = req.getParameterValues("t");
			if ((null != tags) && (tags.length > 0)) {
				for (final String tag : tags) {
					if (StringUtils.isNotBlank(tag)) {
						query.addFilterQuery("+tags:" + ListingQuery.escapeQueryChars(tag));
					}
				}
			}

			// start offset
			final String start = req.getParameter("s");
			if (null != start) {
				final long startIndex = NumberUtils.toLong(start);
				if (startIndex < 0) {
					throw new ApplicationException(400, "startIndex must be greater than or equal to zero");
				}
				query.setStartIndex(startIndex);
			}

			// start offset
			final String rows = req.getParameter("r");
			if (null != rows) {
				final int maxResults = NumberUtils.toInt(rows);
				if ((maxResults <= 0) || (maxResults > 100)) {
					throw new ApplicationException(400, "rows must be greater than zero and less than or equal to 100");
				}

				query.setMaxResults(maxResults);
			}
		}

		final IListingResult result = listingService.findListings(query);

		//		final Future<IListingResult> findListings = listingService.findListings(query, null);
		//		IListingResult result;
		//		try {
		//			result = findListings.get(8, TimeUnit.SECONDS);
		//		} catch (final InterruptedException e) {
		//			Thread.currentThread().interrupt();
		//			throw new ApplicationException(503, "Server Too Busy");
		//		} catch (final TimeoutException e) {
		//			throw new ApplicationException(503, "Server Too Busy");
		//		} catch (final Exception e) {
		//			throw new ApplicationException(e);
		//		}
		if (null == result) {
			resp.sendError(404);
			return;
		}

		//resp.setContentType("application/json");
		if (req.getParameter("text") != null) {
			resp.setContentType("text/plain");
		} else {
			resp.setContentType("application/json");
		}
		resp.setCharacterEncoding("UTF-8");

		final PrintWriter writer = resp.getWriter();
		final JsonGenerator json = new JsonFactory().createJsonGenerator(writer);
		if (req.getParameter("text") != null) {
			json.useDefaultPrettyPrinter();
		}

		if (isSingleListing) {
			writeSingleProductResult(result, json, req);
		} else {
			writeProductsResult(result, json, req);
		}

		json.close();
	}

	/**
	 * Prints a help text.
	 * 
	 * @param req
	 * @param resp
	 * @throws IOException
	 */
	private void doHelp(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
		resp.setContentType("text/plain");
		resp.setCharacterEncoding("UTF-8");
		final PrintWriter writer = resp.getWriter();
		writer.println("JSON Servlet Usage");
		writer.println("==================");
		writer.println();
		writer.print("    Retrieve all products: ");
		writer.println(getBaseUrl(req));
		writer.print("Retrieve a single product: ");
		writer.println(getBaseUrl(req).append("<uripath>"));
		writer.print("                       or: ");
		writer.println(getBaseUrl(req).append(ID_PATH_PREFIX.substring(1)).append("<id>"));
		writer.println();
		writer.println();
		writer.println("Search/Guided Navigation Parameters");
		writer.println("-----------------------------------");
		writer.println();
		writer.println("q ... the query string");
		writer.println("      (see JavaDoc of org.eclipse.gyrex.cds.service.query.ListingQuery#setQuery(String))");
		writer.println("f ... a filter query (multiple possible, will be interpreted as AND; ..&f=..&f=..)");
		writer.println("      (eg. the facet 'filter' attribute from the result set)");
		writer.println("c ... easy retrieval of a category (multiple possible, will be interpreted as AND; ..&c=shirts&c=underwear)");
		writer.println("      (to filter for categories using OR use a filter \"..&f=+category:(shirts underwear)\")");
		writer.println("s ... start index (zero-based, used for paging)");
		writer.println("r ... rows to return (defaults to 10, used for paging)");
		writer.println("t ... easy retrieval of a tag (multiple possible, will be interpreted as AND; ..&t=shirts&t=cool)");
		writer.println("      (to filter for tags using OR use a filter \"..&f=+tags:(shirts cool)\")");
		writer.println();
		writer.println();
		writer.println("Variations");
		writer.println("----------");
		writer.println();
		writer.println("To keep things simple, variations are not searched by default. They should only be relevant");
		writer.println("on product details pages. Thus, they are available in the product details information.");
		writer.println();
		writer.println();
		writer.println("Debug Parameters");
		writer.println("----------------");
		writer.println();
		writer.println("help ... print this help text");
		writer.println("text ... send response as plain/text");
		writer.flush();
	}

	/**
	 * Returns the context.
	 * 
	 * @return the context
	 */
	public IRuntimeContext getContext() {
		return context;
	}

	private void writeFacet(final IListingResultFacet facet, final JsonGenerator json) throws IOException {
		if (null == facet) {
			return;
		}
		json.writeStartObject();

		json.writeFieldName("label");
		json.writeString(facet.getLabel());

		json.writeFieldName("id");
		json.writeString(facet.getId());

		json.writeFieldName("values");
		json.writeStartArray();
		final IListingResultFacetValue[] values = facet.getValues();
		for (final IListingResultFacetValue value : values) {
			json.writeStartObject();
			writeValue("value", value.getValue(), json);
			writeValue("count", value.getCount(), json);
			writeValue("filter", value.toFilterQuery(), json);
			json.writeEndObject();
		}
		json.writeEndArray();

		json.writeEndObject();
	}

	private void writeProduct(final IListing listing, final JsonGenerator json, final HttpServletRequest req, final Enhancer enhancer) throws IOException {
		if (null == listing) {
			return;
		}
		json.writeStartObject();

		writeValue("id", listing.getId(), json);
		writeValue("name", listing.getName(), json);
		writeValue("title", listing.getTitle(), json);
		writeValue("description", listing.getDescription(), json);
		writeValue("uri", getBaseUrl(req).append(listing.getUriPath()).toString(), json);
		writeValue("uripath", listing.getUriPath(), json);

		final IListingAttribute categoryAttribute = listing.getAttribute("category");
		if ((null != categoryAttribute) && (categoryAttribute.getValues().length > 0)) {
			writeValue("category", categoryAttribute.getValues()[0].toString(), json);
		}

		final IListingAttribute priceAttribute = listing.getAttribute("price");
		if ((null != priceAttribute) && (priceAttribute.getValues().length > 0)) {
			// the first price the formated store price
			writeValue("shopPrice", MeasureFormat.getCurrencyFormat(ULocale.GERMANY).format(new CurrencyAmount((Double) priceAttribute.getValues()[0], com.ibm.icu.util.Currency.getInstance("EUR"))), json);
		}

		final IListingAttribute typeAttribute = listing.getAttribute("type");
		if ((null != typeAttribute) && (typeAttribute.getValues().length > 0)) {
			writeValue("type", typeAttribute.getValues()[0].toString(), json);
		}

		final IListingAttribute parentIdAttribute = listing.getAttribute("parentid");
		if ((null != parentIdAttribute) && (parentIdAttribute.getValues().length > 0)) {
			writeValue("parentid", parentIdAttribute.getValues()[0].toString(), json);
		}

		final IListingAttribute[] attributes = listing.getAttributes();
		if (attributes.length > 0) {
			json.writeFieldName("attributes");
			json.writeStartObject();
			final ObjectMapper javaTypeMapper = new ObjectMapper();
			for (final IListingAttribute attribute : attributes) {
				json.writeFieldName(attribute.getName());
				json.writeStartArray();
				for (final Object object : attribute.getValues()) {
					javaTypeMapper.writeValue(json, object);
				}
				json.writeEndArray();
			}
			json.writeEndObject();
		}

		if (null != enhancer) {
			enhancer.enhanceWithinObject(json);
		}

		json.writeEndObject();
	}

	private void writeProductsResult(final IListingResult result, final JsonGenerator json, final HttpServletRequest req) throws IOException {
		json.writeStartObject();

		writeValue("version", "1.0", json);
		writeValue("type", "application/x-gyrex-fanshop-products-json", json);

		json.writeFieldName("query");
		writeQuery(result.getQuery(), json);

		writeValue("queryTime", result.getQueryTime(), json);
		writeValue("numFound", result.getNumFound(), json);
		writeValue("startOffset", result.getStartOffset(), json);

		json.writeFieldName("facets");
		json.writeStartArray();
		for (final IListingResultFacet facet : result.getFacets()) {
			writeFacet(facet, json);
		}
		json.writeEndArray();

		json.writeFieldName("products");
		json.writeStartArray();
		for (final IListing listing : result.getListings()) {
			writeProduct(listing, json, req, null);
		}
		json.writeEndArray();

		json.writeEndObject();
	}

	private void writeQuery(final ListingQuery query, final JsonGenerator json) throws IOException {
		if (null == query) {
			return;
		}
		json.writeStartObject();

		if (null != query.getAdvancedQuery()) {
			writeValue("advancedQuery", query.getAdvancedQuery(), json);
		} else {
			writeValue("query", query.getQuery(), json);
		}

		final List<String> filterQueries = query.getFilterQueries();
		if (!filterQueries.isEmpty()) {
			json.writeFieldName("filters");
			json.writeStartArray();
			for (final String filter : filterQueries) {
				json.writeString(filter);
			}
			json.writeEndArray();
		}

		final Map<String, SortDirection> sortFields = query.getSortFields();
		if (!sortFields.isEmpty()) {
			json.writeFieldName("sortFields");
			json.writeStartObject();
			for (final Entry<String, SortDirection> entry : sortFields.entrySet()) {
				json.writeFieldName(entry.getKey());
				switch (entry.getValue()) {
					case DESCENDING:
						json.writeString("desc");
						break;
					case ASCENDING:
					default:
						json.writeString("asc");
						break;
				}
			}
			json.writeEndObject();
		}

		json.writeFieldName("dimension");
		switch (query.getResultDimension()) {
			case FULL:
				json.writeString("full");
				break;

			case COMPACT:
			default:
				json.writeString("compact");
				break;
		}

		json.writeEndObject();
	}

	private void writeSingleProductResult(final IListingResult result, final JsonGenerator json, final HttpServletRequest req) throws IOException {
		json.writeStartObject();

		writeValue("version", "1.0", json);
		writeValue("type", "application/x-gyrex-fanshop-product-json", json);

		json.writeFieldName("query");
		writeQuery(result.getQuery(), json);

		writeValue("queryTime", result.getQueryTime(), json);
		//writeValue("numFound", result.getNumFound(), json);
		//writeValue("startOffset", result.getStartOffset(), json);

		final IListing[] listings = result.getListings();
		if (listings.length == 1) {
			json.writeFieldName("product");
			final IListing product = listings[0];
			writeProduct(product, json, req, new Enhancer() {

				@Override
				public void enhanceWithinObject(final JsonGenerator json) throws IOException {
					//					final String productType = (String) product.getAttribute("type").getValues()[0];
					//					if ("variable-product".equals(productType)) {
					//						final Object[] variationids = product.getAttribute("variationids").getValues();
					//						json.writeFieldName("variations");
					//						json.writeStartObject();
					//						final IListingManager manager = ModelUtil.getManager(IListingManager.class, getContext());
					//						for (final Object variationId : variationids) {
					//							final IListing variation = manager.findById((String) variationId);
					//							if (null != variation) {
					//								json.writeFieldName((String) variationId);
					//								writeProduct(variation, json, req, null);
					//							}
					//						}
					//						json.writeEndObject();
					//					} else if ("variation".equals(productType)) {
					//						final String masterid = (String) product.getAttribute("parentid").getValues()[0];
					//						final IListingManager manager = ModelUtil.getManager(IListingManager.class, getContext());
					//						final IListing master = manager.findById(masterid);
					//						if (null != master) {
					//							json.writeFieldName("master");
					//							writeProduct(master, json, req, null);
					//						}
					//					}
				}
			});
		}

		json.writeEndObject();
	}

	private void writeValue(final String name, final long value, final JsonGenerator json) throws IOException {
		json.writeFieldName(name);
		json.writeNumber(value);
	}

	private void writeValue(final String name, final String value, final JsonGenerator json) throws IOException, JsonGenerationException {
		if (StringUtils.isNotBlank(value)) {
			json.writeFieldName(name);
			json.writeString(value);
		}
	}
}
