package com.openchat.secureim.storage;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.TransactionIsolationLevel;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.BinderFactory;
import org.skife.jdbi.v2.sqlobject.BindingAnnotation;
import org.skife.jdbi.v2.sqlobject.GetGeneratedKeys;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.Transaction;
import org.skife.jdbi.v2.sqlobject.customizers.Mapper;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;

public abstract class Accounts {

  private static final String ID     = "id";
  private static final String NUMBER = "number";
  private static final String DATA   = "data";

  private static final ObjectMapper mapper = new ObjectMapper();

  static {
    mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
    mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
  }

  @SqlUpdate("INSERT INTO accounts (" + NUMBER + ", " + DATA + ") VALUES (:number, CAST(:data AS json))")
  @GetGeneratedKeys
  abstract long insertStep(@AccountBinder Account account);

  @SqlUpdate("DELETE FROM accounts WHERE " + NUMBER + " = :number")
  abstract void removeAccount(@Bind("number") String number);

  @SqlUpdate("UPDATE accounts SET " + DATA + " = CAST(:data AS json) WHERE " + NUMBER + " = :number")
  abstract void update(@AccountBinder Account account);

  @Mapper(AccountMapper.class)
  @SqlQuery("SELECT * FROM accounts WHERE " + NUMBER + " = :number")
  abstract Account get(@Bind("number") String number);

  @SqlQuery("SELECT COUNT(DISTINCT " + NUMBER + ") from accounts")
  abstract long getCount();

  @Mapper(AccountMapper.class)
  @SqlQuery("SELECT * FROM accounts OFFSET :offset LIMIT :limit")
  abstract List<Account> getAll(@Bind("offset") int offset, @Bind("limit") int length);

  @Mapper(AccountMapper.class)
  @SqlQuery("SELECT * FROM accounts")
  public abstract Iterator<Account> getAll();

  @Transaction(TransactionIsolationLevel.SERIALIZABLE)
  public long create(Account account) {
    removeAccount(account.getNumber());
    return insertStep(account);
  }

  public static class AccountMapper implements ResultSetMapper<Account> {
    @Override
    public Account map(int i, ResultSet resultSet, StatementContext statementContext)
        throws SQLException
    {
      try {
        return mapper.readValue(resultSet.getString(DATA), Account.class);
      } catch (IOException e) {
        throw new SQLException(e);
      }
    }
  }

  @BindingAnnotation(AccountBinder.AccountBinderFactory.class)
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.PARAMETER})
  public @interface AccountBinder {
    public static class AccountBinderFactory implements BinderFactory {
      @Override
      public Binder build(Annotation annotation) {
        return new Binder<AccountBinder, Account>() {
          @Override
          public void bind(SQLStatement<?> sql,
                           AccountBinder accountBinder,
                           Account account)
          {
            try {
              String serialized = mapper.writeValueAsString(account);

              sql.bind(NUMBER, account.getNumber());
              sql.bind(DATA, serialized);
            } catch (JsonProcessingException e) {
              throw new IllegalArgumentException(e);
            }
          }
        };
      }
    }
  }

}
