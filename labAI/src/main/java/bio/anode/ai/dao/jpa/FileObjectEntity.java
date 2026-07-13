package bio.anode.ai.dao.jpa;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;

/** Stores an uploaded OpenAI "file" (its bytes + metadata). */
@Entity
@Getter
@Setter
@ToString(exclude = "bytes")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "file_object")
public class FileObjectEntity {

    @Id
    private String id;

    private String filename;

    /** Raw file content. Lazy so list/metadata queries don't load blobs. */
    @Lob
    @Basic(fetch = FetchType.LAZY)
    private byte[] bytes;

    private long bytesSize;

    private String purpose;

    /** uploaded | processed | error */
    private String status;

    private long createdAt;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oClass = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> tClass = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : getClass();
        if (tClass != oClass) return false;
        FileObjectEntity that = (FileObjectEntity) o;
        return getId() != null && Objects.equals(getId(), that.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}
