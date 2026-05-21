package id.ac.ui.cs.advprog.bidmart.auction.repository;

import id.ac.ui.cs.advprog.bidmart.auction.model.Auction;
import id.ac.ui.cs.advprog.bidmart.auction.model.AuctionStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuctionSpecificationTest {

    private Root<Auction> root;
    private CriteriaQuery<?> query;
    private CriteriaBuilder criteriaBuilder;
    private Path<Object> statusPath;
    private Path<Object> pricePath;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        root = mock(Root.class);
        query = mock(CriteriaQuery.class);
        criteriaBuilder = mock(CriteriaBuilder.class);
        statusPath = mock(Path.class);
        pricePath = mock(Path.class);

        when(root.get("status")).thenReturn(statusPath);
        when(root.get("currentPrice")).thenReturn(pricePath);
        when(criteriaBuilder.and(any())).thenReturn(mock(Predicate.class));
    }

    @Test
    void testFilterByAllNull() {
        Specification<Auction> spec = AuctionSpecification.filterBy(null, null, null);
        spec.toPredicate(root, query, criteriaBuilder);
        
        assertNotNull(spec);
        verify(criteriaBuilder).and(new Predicate[0]);
    }

    @Test
    void testFilterByStatus() {
        Specification<Auction> spec = AuctionSpecification.filterBy(AuctionStatus.ACTIVE, null, null);
        spec.toPredicate(root, query, criteriaBuilder);

        verify(criteriaBuilder).equal(statusPath, AuctionStatus.ACTIVE);
    }

    @Test
    void testFilterByMinPrice() {
        Specification<Auction> spec = AuctionSpecification.filterBy(null, 100L, null);
        spec.toPredicate(root, query, criteriaBuilder);

        verify(criteriaBuilder).greaterThanOrEqualTo(any(), eq(100L));
    }

    @Test
    void testFilterByMaxPrice() {
        Specification<Auction> spec = AuctionSpecification.filterBy(null, null, 500L);
        spec.toPredicate(root, query, criteriaBuilder);

        verify(criteriaBuilder).lessThanOrEqualTo(any(), eq(500L));
    }

    @Test
    void testFilterByAll() {
        Specification<Auction> spec = AuctionSpecification.filterBy(AuctionStatus.ACTIVE, 100L, 500L);
        spec.toPredicate(root, query, criteriaBuilder);

        verify(criteriaBuilder).equal(statusPath, AuctionStatus.ACTIVE);
        verify(criteriaBuilder).greaterThanOrEqualTo(any(), eq(100L));
        verify(criteriaBuilder).lessThanOrEqualTo(any(), eq(500L));
    }
}
