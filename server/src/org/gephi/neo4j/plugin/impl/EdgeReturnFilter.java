/*
Copyright 2008-2010 Gephi
Authors : Martin Škurla
Website : http://www.gephi.org

This file is part of Gephi.

Gephi is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as
published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

Gephi is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with Gephi.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.gephi.neo4j.plugin.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.gephi.neo4j.plugin.api.FilterDescription;
import org.gephi.neo4j.plugin.api.FilterOperator;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.helpers.Predicate;

/**
 *
 * @author Martin Škurla
 */
class EdgeReturnFilter implements Predicate<Path> {

    private final Map<PropertyParsingKey, Object> mapper;
    private final Set<PropertyParsingKey> notParsableProperties;
    private final Collection<FilterDescription> filterDescriptions;
    private final boolean restrictMode;
    private final boolean matchCase;

    EdgeReturnFilter(Collection<FilterDescription> filterDescriptions, boolean restrictMode, boolean matchCase) {
        this.filterDescriptions = filterDescriptions;
        this.restrictMode = restrictMode;
        this.matchCase = matchCase;

        this.mapper = new HashMap<PropertyParsingKey, Object>();
        this.notParsableProperties = new HashSet<PropertyParsingKey>();
    }

    @Override
    public boolean accept(Path path) {     	
    	if (path.lastRelationship() == null)
    	{
    		return true;
    	}   
    	
        return accept(path.lastRelationship());
    }

    public boolean accept(Relationship edge) {
        for (FilterDescription filterDescription : filterDescriptions) {
            if (edge.hasProperty(filterDescription.getPropertyKey())) {
                Object edgePropertyValue = edge.getProperty(filterDescription.getPropertyKey());

                boolean isValid = doValidation(edgePropertyValue, filterDescription.getOperator(), filterDescription.getPropertyValue());

                if (isValid == false) {
                    return false;
                }
            } else {
                return !restrictMode;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private <T> T parseValue(String expectedValue, Class<T> finalType) throws NotParsableException {
        PropertyParsingKey key = new PropertyParsingKey(expectedValue, finalType);
        if (notParsableProperties.contains(key)) {
            throw new NotParsableException();
        }

        Object parsedValue = mapper.get(key);

       // if (parsedValue == null) {
            try {
                parsedValue = TypeHelper.parseFromString(expectedValue, finalType);
                mapper.put(key, parsedValue);
                return (T) parsedValue;
            } catch (NotParsableException npe) {
                notParsableProperties.add(key);
                throw npe;
            }
       // }

        //throw new AssertionError();
    }

    private boolean doValidation(Object edgePropertyValue, FilterOperator operator, String expectedValue) {
        try {
            if (TypeHelper.isWholeNumber(edgePropertyValue)) {
                return operator.executeOnWholeNumbers((Number) edgePropertyValue,
                        parseValue(expectedValue, Long.class));
            } else if (TypeHelper.isRealNumber(edgePropertyValue)) {
                return operator.executeOnRealNumbers((Number) edgePropertyValue,
                        parseValue(expectedValue, Double.class));
            } else if (TypeHelper.isBoolean(edgePropertyValue)) {
                return operator.executeOnBooleans((Boolean) edgePropertyValue,
                        parseValue(expectedValue, Boolean.class));
            } else if (TypeHelper.isCharacter(edgePropertyValue)) {
                return operator.executeOnCharacters((Character) edgePropertyValue,
                        parseValue(expectedValue, Character.class),
                        matchCase);
            } else if (TypeHelper.isArray(edgePropertyValue)) {
                if (TypeHelper.isWholeNumberArray(edgePropertyValue)) {
                    return operator.executeOnWholeNumberArrays(edgePropertyValue,
                            parseValue(expectedValue, Long[].class));
                } else if (TypeHelper.isRealNumberArray(edgePropertyValue)) {
                    return operator.executeOnRealNumberArrays(edgePropertyValue,
                            parseValue(expectedValue, Double[].class));
                } else if (TypeHelper.isBooleanArray(edgePropertyValue)) {
                    return operator.executeOnBooleanArrays(edgePropertyValue,
                            parseValue(expectedValue, Boolean[].class));
                } else if (TypeHelper.isCharacterArray(edgePropertyValue)) {
                    return operator.executeOnCharacterArrays(edgePropertyValue,
                            parseValue(expectedValue, Character[].class),
                            matchCase);
                } else if (TypeHelper.isStringArray(edgePropertyValue)) {
                    return operator.executeOnStringArrays(edgePropertyValue,
                            parseValue(expectedValue, String[].class),
                            matchCase);
                } else {
                    throw new AssertionError();
                }
            } else if (TypeHelper.isString(edgePropertyValue)) {
                return operator.executeOnStrings((String) edgePropertyValue, expectedValue, matchCase);
            } else {
                throw new AssertionError();
            }
        } catch (NotParsableException npe) {
            return false;
        }
    }

    private static class PropertyParsingKey {

        private final String textValue;
        private final Class<?> finalType;

        PropertyParsingKey(String textValue, Class<?> finalType) {
            this.textValue = textValue;
            this.finalType = finalType;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof PropertyParsingKey)) {
                return false;
            }

            PropertyParsingKey key = (PropertyParsingKey) o;
            return this.finalType == key.finalType
                    || this.textValue.equals(key.textValue);
        }

        @Override
        public int hashCode() {
            return textValue.hashCode() + finalType.hashCode();
        }
    }
}
