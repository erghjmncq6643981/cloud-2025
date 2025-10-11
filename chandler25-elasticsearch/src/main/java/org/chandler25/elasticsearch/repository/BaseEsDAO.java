package org.chandler25.elasticsearch.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.*;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.*;
import lombok.extern.slf4j.Slf4j;
import org.chandler25.elasticsearch.dto.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public abstract class BaseEsDAO {

	@Autowired
	protected ElasticsearchClient elasticsearchClient;
	
	protected abstract String  getIndex();


    public boolean batchCreate(List<? extends BaseDTO> list) {
        if(list == null || list.isEmpty()) {
            return false;
        }

        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
        list.forEach(each -> {
            bulkBuilder.operations(op -> op
                    .index(idx -> idx
                            .index(getIndex())
                            .id(each.getId())
                            .document(each)
                    )
            );
        });

        try {
            BulkResponse response = elasticsearchClient.bulk(bulkBuilder.build());
            return !response.errors();
        } catch (Exception e) {
            log.error("batchCreate失败", e);
            return false;
        }
    }
	 
    /**
     * 搜索条件
     * @param queryDTO
     * @return
     */
    public Query queryBuilder(EsBoolQueryDTO queryDTO) {
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

        if(EsBoolQueryDTO.Type.MUST == queryDTO.getType()) {
            // 嵌套 Bool 查询
            queryDTO.getBoolQueries().forEach(boolQuery ->
                    boolBuilder.must(queryBuilder(boolQuery)));

            // Wildcard 查询
            queryDTO.getWildcardQueries().forEach(wildcardQuery ->
                    boolBuilder.must(WildcardQuery.of(w -> w
                            .field(wildcardQuery.getKey())
                            .value(wildcardQuery.getValue()))._toQuery()));

            // Match 查询
            queryDTO.getMatchQueries().forEach(matchQuery ->
                    boolBuilder.must(MatchQuery.of(m -> m
                            .field(matchQuery.getKey())
                            .query((FieldValue) matchQuery.getValue()))._toQuery()));

            // Terms 查询
            queryDTO.getTermsQueries().forEach(termsQuery ->
                    boolBuilder.must(TermsQuery.of(t -> t
                            .field(termsQuery.getKey())
                            .terms(TermsQueryField.of(tf -> tf.value(
                                    termsQuery.getValue().stream()
                                            .map(FieldValue::of)
                                            .collect(Collectors.toList())))))._toQuery()));

            // Exist 查询
            queryDTO.getExistQueries().forEach(existQuery ->
                    boolBuilder.must(ExistsQuery.of(m -> m
                            .field(existQuery.getKey()))._toQuery()));
            // Range 查询
            queryDTO.getRangeQueries().forEach(rangeQuery ->
                    boolBuilder.must(RangeQuery.of(r -> r
                            .field(rangeQuery.getKey())
                            .from(String.valueOf(rangeQuery.getFrom()))
                            .to(String.valueOf(rangeQuery.getTo())))._toQuery()));

            // Multi 查询
            queryDTO.getMultiQueries().forEach(multiQuery ->
                    boolBuilder.must(MultiMatchQuery.of(m -> m.fields(multiQuery.getKey())
                            .query(multiQuery.getValue()))._toQuery()));

            // MatchPhrase 查询
            queryDTO.getMatchPhraseQueries().forEach(matchPhraseQuery ->
                    boolBuilder.must(MatchPhraseQuery.of(m -> m
                            .field(matchPhraseQuery.getKey())
                            .query(matchPhraseQuery.getValue()))._toQuery()));

            // MatchPhrasePrefix 查询
            queryDTO.getMatchPhrasePrefixQueries().forEach(matchPhrasePrefixQuery ->
                    boolBuilder.must(MatchPhrasePrefixQuery.of(m -> m
                            .field(matchPhrasePrefixQuery.getKey())
                            .query(matchPhrasePrefixQuery.getValue()))._toQuery()));
        }

        if(EsBoolQueryDTO.Type.SHOULD == queryDTO.getType()) {
            // 嵌套 Bool 查询
            queryDTO.getBoolQueries().forEach(boolQuery ->
                    boolBuilder.should(queryBuilder(boolQuery)));

            // Wildcard 查询
            queryDTO.getWildcardQueries().forEach(wildcardQuery ->
                    boolBuilder.should(WildcardQuery.of(w -> w
                            .field(wildcardQuery.getKey())
                            .value(wildcardQuery.getValue()))._toQuery()));

            // Match 查询
            queryDTO.getMatchQueries().forEach(matchQuery ->
                    boolBuilder.should(MatchQuery.of(m -> m
                            .field(matchQuery.getKey())
                            .query((FieldValue) matchQuery.getValue()))._toQuery()));

            // Terms 查询
            queryDTO.getTermsQueries().forEach(termsQuery ->
                    boolBuilder.should(TermsQuery.of(t -> t
                            .field(termsQuery.getKey())
                            .terms(TermsQueryField.of(tf -> tf.value(
                                    termsQuery.getValue().stream()
                                            .map(FieldValue::of)
                                            .collect(Collectors.toList())))))._toQuery()));

            // Exist 查询
            queryDTO.getExistQueries().forEach(existQuery ->
                    boolBuilder.should(ExistsQuery.of(m -> m
                            .field(existQuery.getKey()))._toQuery()));
            // Range 查询
            queryDTO.getRangeQueries().forEach(rangeQuery ->
                    boolBuilder.should(RangeQuery.of(r -> r
                            .field(rangeQuery.getKey())
                            .from(String.valueOf(rangeQuery.getFrom()))
                            .to(String.valueOf(rangeQuery.getTo())))._toQuery()));

            // Multi 查询
            queryDTO.getMultiQueries().forEach(multiQuery ->
                    boolBuilder.should(MultiMatchQuery.of(m -> m.fields(multiQuery.getKey())
                            .query(multiQuery.getValue()))._toQuery()));

            // MatchPhrase 查询
            queryDTO.getMatchPhraseQueries().forEach(matchPhraseQuery ->
                    boolBuilder.should(MatchPhraseQuery.of(m -> m
                            .field(matchPhraseQuery.getKey())
                            .query(matchPhraseQuery.getValue()))._toQuery()));

            // MatchPhrasePrefix 查询
            queryDTO.getMatchPhrasePrefixQueries().forEach(matchPhrasePrefixQuery ->
                    boolBuilder.should(MatchPhrasePrefixQuery.of(m -> m
                            .field(matchPhrasePrefixQuery.getKey())
                            .query(matchPhrasePrefixQuery.getValue()))._toQuery()));
        }

        if(EsBoolQueryDTO.Type.MUST_NOT == queryDTO.getType()) {
            // 嵌套 Bool 查询
            queryDTO.getBoolQueries().forEach(boolQuery ->
                    boolBuilder.mustNot(queryBuilder(boolQuery)));

            // Wildcard 查询
            queryDTO.getWildcardQueries().forEach(wildcardQuery ->
                    boolBuilder.mustNot(WildcardQuery.of(w -> w
                            .field(wildcardQuery.getKey())
                            .value(wildcardQuery.getValue()))._toQuery()));

            // Match 查询
            queryDTO.getMatchQueries().forEach(matchQuery ->
                    boolBuilder.mustNot(MatchQuery.of(m -> m
                            .field(matchQuery.getKey())
                            .query((FieldValue) matchQuery.getValue()))._toQuery()));

            // Terms 查询
            queryDTO.getTermsQueries().forEach(termsQuery ->
                    boolBuilder.mustNot(TermsQuery.of(t -> t
                            .field(termsQuery.getKey())
                            .terms(TermsQueryField.of(tf -> tf.value(
                                    termsQuery.getValue().stream()
                                            .map(FieldValue::of)
                                            .collect(Collectors.toList())))))._toQuery()));

            // Exist 查询
            queryDTO.getExistQueries().forEach(existQuery ->
                    boolBuilder.mustNot(ExistsQuery.of(m -> m
                            .field(existQuery.getKey()))._toQuery()));
            // Range 查询
            queryDTO.getRangeQueries().forEach(rangeQuery ->
                    boolBuilder.mustNot(RangeQuery.of(r -> r
                            .field(rangeQuery.getKey())
                            .from(String.valueOf(rangeQuery.getFrom()))
                            .to(String.valueOf(rangeQuery.getTo())))._toQuery()));

            // Multi 查询
            queryDTO.getMultiQueries().forEach(multiQuery ->
                    boolBuilder.mustNot(MultiMatchQuery.of(m -> m.fields(multiQuery.getKey())
                            .query(multiQuery.getValue()))._toQuery()));

            // MatchPhrase 查询
            queryDTO.getMatchPhraseQueries().forEach(matchPhraseQuery ->
                    boolBuilder.mustNot(MatchPhraseQuery.of(m -> m
                            .field(matchPhraseQuery.getKey())
                            .query(matchPhraseQuery.getValue()))._toQuery()));

            // MatchPhrasePrefix 查询
            queryDTO.getMatchPhrasePrefixQueries().forEach(matchPhrasePrefixQuery ->
                    boolBuilder.mustNot(MatchPhrasePrefixQuery.of(m -> m
                            .field(matchPhrasePrefixQuery.getKey())
                            .query(matchPhrasePrefixQuery.getValue()))._toQuery()));
        }

        // Geo Distance 查询
        if(queryDTO.getGeoQuery() != null) {
            EsGeoDistanceQueryDTO geoQuery = queryDTO.getGeoQuery();
            boolBuilder.filter(GeoDistanceQuery.of(g -> g
                    .field(geoQuery.getKey())
                    .location(GeoLocation.of(gl -> gl.latlon(LatLonGeoLocation.of(ll -> ll
                            .lat(geoQuery.getLat())
                            .lon(geoQuery.getLon())))))
                    .distance(String.valueOf(geoQuery.getDistance() != null ?
                            geoQuery.getDistance() : 10.0) + "km"))._toQuery());
        }

        return boolBuilder.build()._toQuery();
    }

}
