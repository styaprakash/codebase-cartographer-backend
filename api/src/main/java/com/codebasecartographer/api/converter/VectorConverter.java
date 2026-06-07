package com.codebasecartographer.api.converter;

import com.pgvector.PGvector;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.sql.SQLException;

@Converter(autoApply = true)
public class VectorConverter implements AttributeConverter<float[], Object> {

    @Override
    public Object convertToDatabaseColumn(float[] attribute) {
        if (attribute == null) {
            return null;
        }
        return new PGvector(attribute);
    }

    @Override
    public float[] convertToEntityAttribute(Object dbData) {
        if (dbData == null) {
            return null;
        }
        
        if (dbData instanceof PGvector) {
            return ((PGvector) dbData).toArray();
        } else if (dbData instanceof String) {
            try {
                return new PGvector((String) dbData).toArray();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to convert PGvector string", e);
            }
        } else if (dbData instanceof org.postgresql.util.PGobject pgObject) {
            try {
                return new PGvector(pgObject.getValue()).toArray();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to convert PGobject to vector", e);
            }
        }
        
        throw new IllegalArgumentException("Unknown type for vector conversion: " + dbData.getClass());
    }
}
