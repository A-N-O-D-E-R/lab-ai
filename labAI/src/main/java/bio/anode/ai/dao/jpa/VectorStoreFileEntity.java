package bio.anode.ai.dao.jpa;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;

/** Association between a {@link VectorStoreEntity} and a {@link FileObjectEntity} that was ingested into it. */
@Entity
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "vector_store_file",
        uniqueConstraints = @UniqueConstraint(columnNames = {"vectorStoreId", "fileId"}))
public class VectorStoreFileEntity {

    @Id
    private String id;

    private String vectorStoreId;

    private String fileId;

    /** in_progress | completed | failed */
    private String status;

    private int chunkCount;

    @Column(length = 2000)
    private String lastError;

    private long createdAt;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oClass = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> tClass = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : getClass();
        if (tClass != oClass) return false;
        VectorStoreFileEntity that = (VectorStoreFileEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
