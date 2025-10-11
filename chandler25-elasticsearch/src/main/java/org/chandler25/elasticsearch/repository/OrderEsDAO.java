package org.chandler25.elasticsearch.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.*;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.chandler25.elasticsearch.config.ElasticConfig;
import org.chandler25.elasticsearch.dto.EsGeoSortQueryDTO;
import org.chandler25.elasticsearch.dto.EsQueryDTO;
import org.chandler25.elasticsearch.dto.EsSortQueryDTO;
import org.chandler25.elasticsearch.dto.resp.PageResultRes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Repository
@RefreshScope
public class OrderEsDAO extends BaseEsDAO {

    @Autowired
    private ElasticConfig elasticConfig;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Value("${order.es.search.type:}")
    private String orderEsSearchType;

    @Override
    protected String getIndex() {
        return "order";
    }

    /**
     * 通用订单查询
     *
     * @param esQueryDTO
     * @return
     */
    public <T> PageResultRes<T> search(EsQueryDTO esQueryDTO, Class<T> tClass) {
        PageResultRes<T> result = new PageResultRes<>();

        try {
            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(elasticConfig.getOrderIndex())
                    .from((esQueryDTO.getPage()) * esQueryDTO.getPageSize())
                    .size(esQueryDTO.getPageSize())
                    .query(queryBuilder(esQueryDTO.getBoolQuery()))
                    .sort(buildSorts(esQueryDTO))
            );

            SearchResponse<T> response = elasticsearchClient.search(searchRequest, tClass);

            result.setTotal(response.hits().total().value());
            result.setPage(esQueryDTO.getPage());
            result.setPageSize(esQueryDTO.getPageSize());
            result.calcTotalPage();

            List<T> results = response.hits().hits().stream()
                    .map(hit -> hit.source())
                    .collect(Collectors.toList());
            result.setResults(results);

        } catch (Exception e) {
            log.error("es查询失败", e);
        }

        return result;
    }

    private List<SortOptions> buildSorts(EsQueryDTO esQueryDTO) {
        List<SortOptions> sorts = new ArrayList<>();

        esQueryDTO.getSortQueries().stream()
                .sorted(Comparator.comparingInt(EsSortQueryDTO::getOrder))
                .forEach(sortQuery -> {
                    SortOrder order = EsSortQueryDTO.OrderType.ASC == sortQuery.getOrderType()
                            ? SortOrder.Asc : SortOrder.Desc;
                    sorts.add(SortOptions.of(s -> s.field(FieldSort.of(f -> f
                            .field(sortQuery.getKey())
                            .order(order)))));
                });
        List<EsGeoSortQueryDTO> geoSortQueryDTOS = esQueryDTO.getGeoSortQueries();
        if (geoSortQueryDTOS == null || geoSortQueryDTOS.isEmpty()) {
            return sorts;
        }
        geoSortQueryDTOS.sort(Comparator.comparingInt(EsGeoSortQueryDTO::getOrder));
        geoSortQueryDTOS.forEach(esGeoSortQueryDTO -> {
            GeoDistanceSort geoSort = GeoDistanceSort.of(geo ->
                    geo.order(SortOrder.valueOf(esGeoSortQueryDTO.getOrderType().name()))
                            .field(esGeoSortQueryDTO.getKey())
                            .unit(DistanceUnit.valueOf(esGeoSortQueryDTO.getGeoUnitType().name()))
                            .distanceType(GeoDistanceType.Arc)
                            .location(gl -> gl.latlon(ll -> ll.lat(esGeoSortQueryDTO.getLat()).lon(esGeoSortQueryDTO.getLon())))
            );
            sorts.add(SortOptions.of(s -> s.geoDistance(geoSort)));

        });
        return sorts;
    }

}
