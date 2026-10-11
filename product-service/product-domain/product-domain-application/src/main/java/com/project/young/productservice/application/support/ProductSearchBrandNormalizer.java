package com.project.young.productservice.application.support;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Shared brand normalization for search filters and indexed catalog documents. */
public final class ProductSearchBrandNormalizer {

    private static final Pattern EDGE_WHITESPACE = Pattern.compile("^\\s+|\\s+$", Pattern.UNICODE_CHARACTER_CLASS);

    private ProductSearchBrandNormalizer() {
    }

    public static String normalizeLabel(String brand) {
        if (brand == null) {
            return null;
        }
        String label = EDGE_WHITESPACE.matcher(Normalizer.normalize(brand, Normalizer.Form.NFC)).replaceAll("");
        return label.isEmpty() ? null : label;
    }

    public static String normalizeKey(String brand) {
        String label = normalizeLabel(brand);
        return label == null ? null : normalizeLabel(label.toLowerCase(Locale.ROOT));
    }
}
