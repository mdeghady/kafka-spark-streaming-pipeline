/**
 * Autogenerated by Avro
 *
 * DO NOT EDIT DIRECTLY
 */
package com.kafka.schema.avro;
@org.apache.avro.specific.AvroGenerated
public enum GenderType implements org.apache.avro.generic.GenericEnumSymbol<GenderType> {
  male, female  ;
  public static final org.apache.avro.Schema SCHEMA$ = new org.apache.avro.Schema.Parser().parse("{\"type\":\"enum\",\"name\":\"GenderType\",\"namespace\":\"com.kafka.schema.avro\",\"symbols\":[\"male\",\"female\"]}");
  public static org.apache.avro.Schema getClassSchema() { return SCHEMA$; }

  @Override
  public org.apache.avro.Schema getSchema() { return SCHEMA$; }
}