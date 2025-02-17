/*
 * Copyright 2015 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.inferred.freebuilder.processor.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.transform;

import static org.inferred.freebuilder.processor.util.feature.SourceLevel.SOURCE_LEVEL;

import static java.util.Arrays.asList;
import static java.util.Collections.nCopies;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;

import org.inferred.freebuilder.processor.util.feature.StaticFeatureSet;

import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleElementVisitor6;
import javax.lang.model.util.SimpleTypeVisitor6;

public class ParameterizedType extends ValueType implements Excerpt {

  public static ParameterizedType from(TypeElement typeElement) {
    return new ParameterisedTypeForElementVisitor().visitType(typeElement, null);
  }

  public static ParameterizedType from(DeclaredType declaredType) {
    return new ParameterisedTypeForMirrorVisitor().visitDeclared(declaredType, null);
  }

  public static ParameterizedType from(Class<?> cls) {
    return new ParameterizedType(QualifiedName.of(cls), asList(cls.getTypeParameters()));
  }

  private final QualifiedName qualifiedName;
  private final List<?> typeParameters;

  ParameterizedType(QualifiedName qualifiedName, List<?> typeParameters) {
    this.qualifiedName = checkNotNull(qualifiedName);
    this.typeParameters = checkNotNull(typeParameters);
  }

  public String getSimpleName() {
    return qualifiedName.getSimpleName();
  }

  public QualifiedName getQualifiedName() {
    return qualifiedName;
  }

  public boolean isParameterized() {
    return !typeParameters.isEmpty();
  }

  @Override
  public void addTo(SourceBuilder source) {
    source.add("%s%s", qualifiedName, typeParameters());
  }

  /**
   * Returns a new {@link ParameterizedType} of the same length as this type, filled with
   * {@code parameters}.
   */
  public ParameterizedType withParameters(TypeMirror... parameters) {
    checkArgument(typeParameters.size() == parameters.length,
        "Need %s parameters for %s but got %s", typeParameters.size(), this, parameters.length);
    return new ParameterizedType(qualifiedName, ImmutableList.copyOf(parameters));
  }

  /**
   * Returns a new {@link ParameterizedType} of the same length as this type, filled with wildcards
   * ("?").
   */
  public ParameterizedType withWildcards() {
    if (typeParameters.isEmpty()) {
      return this;
    }
    return new ParameterizedType(qualifiedName, nCopies(typeParameters.size(), "?"));
  }

  /**
   * Returns a source excerpt suitable for constructing an instance of this type, including "new"
   * keyword but excluding brackets.
   *
   * <p>In Java 7+, we can use the diamond operator. Otherwise, we write out the type parameters
   * in full.
   */
  public Excerpt constructor() {
    return new Excerpt() {
      @Override public void addTo(SourceBuilder source) {
        if (isParameterized() && source.feature(SOURCE_LEVEL).supportsDiamondOperator()) {
          source.add("new %s<>", qualifiedName);
        } else {
          source.add("new %s", ParameterizedType.this);
        }
      }
    };
  }

  /**
   * Returns a source excerpt suitable for declaring this type, i.e. {@code SimpleName<...>}
   */
  public Excerpt declaration() {
    return new Excerpt() {
      @Override public void addTo(SourceBuilder source) {
        source.add("%s%s", qualifiedName.getSimpleName(), typeParameters());
      }
    };
  }

  /**
   * Returns a source excerpt of the type parameters of this type, including angle brackets.
   */
  public Excerpt typeParameters() {
    return new Excerpt() {
      @Override public void addTo(SourceBuilder source) {
        if (!typeParameters.isEmpty()) {
          String prefix = "<";
          for (Object typeParameter : typeParameters) {
            source.add("%s%s", prefix, typeParameter);
            prefix = ", ";
          }
          source.add(">");
        }
      }
    };
  }

  /**
   * Returns a source excerpt of a JavaDoc link to this type.
   */
  public Excerpt javadocLink() {
    return new Excerpt() {
      @Override public void addTo(SourceBuilder source) {
        source.add("{@link %s}", getQualifiedName());
      }
    };
  }

  /**
   * Returns a source excerpt of a JavaDoc link to a no-args method on this type.
   */
  public Excerpt javadocNoArgMethodLink(final String memberName) {
    return new Excerpt() {
      @Override public void addTo(SourceBuilder source) {
        source.add("{@link %s#%s()}", getQualifiedName(), memberName);
      }
    };
  }

  @Override
  public String toString() {
    // Only used when debugging, so an empty feature set is fine.
    return new SourceStringBuilder(new TypeShortener.NeverShorten(), new StaticFeatureSet())
        .add(this)
        .toString();
  }

  @Override
  protected void addFields(FieldReceiver fields) {
    fields.add("qualifiedName", qualifiedName);
    fields.add("typeParameters", typeParameters);
  }

  private static final class ParameterisedTypeForElementVisitor
      extends SimpleElementVisitor6<Object, Void> implements Function<Element, Object> {

    @Override
    public Object apply(Element e) {
      return this.visit(e, null);
    }

    @Override
    public ParameterizedType visitType(TypeElement e, Void p) {
      return new ParameterizedType(
          QualifiedName.of(e),
          ImmutableList.copyOf(transform(e.getTypeParameters(), this)));
    }

    @Override
    protected Object defaultAction(Element e, Void p) {
      return e;
    }
  }

  private static final class ParameterisedTypeForMirrorVisitor
      extends SimpleTypeVisitor6<Object, Void> implements Function<TypeMirror, Object> {

    @Override
    public Object apply(TypeMirror t) {
      return this.visit(t);
    }

    @Override
    public ParameterizedType visitDeclared(DeclaredType t, Void p) {
      return new ParameterizedType(
          QualifiedName.of((TypeElement) t.asElement()),
          ImmutableList.copyOf(transform(t.getTypeArguments(), this)));
    }

    @Override
    protected Object defaultAction(TypeMirror e, Void p) {
      return e;
    }
  }
}
