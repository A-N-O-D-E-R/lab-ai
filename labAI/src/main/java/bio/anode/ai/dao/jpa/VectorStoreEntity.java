package bio.anode.ai.dao.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;

/**
 * An OpenAI "vector store" (a named knowledge collection). Chunk vectors live in the pgvector
 * {@code okf_vectors} table, scoped by the {@code vector_store_id} metadata; this entity holds the
 * OpenAI-facing metadata and file counts.
 */
@Entity
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "vector_store")
public class VectorStoreEntity {

    @Id
    private String id;

    private String name;

    /** completed | in_progress */
    private String status;

    private long createdAt;

    private int fileCountCompleted;

    private int fileCountInProgress;

    private int fileCountFailed;

    private int fileCountTotal;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oClass = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> tClass = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : getClass();
        if (tClass != oClass) return false;
        VectorStoreEntity that = (VectorStoreEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
