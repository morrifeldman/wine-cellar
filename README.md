[Good instructions for getting started with PostgreSQL](https://www.digitalocean.com/community/tutorials/how-to-install-postgresql-on-ubuntu-20-04-quickstart)

```
> sudo -i -u postgres
> createuser wine_cellar
> psql
# alter user wine_cellar with password 'chianti';
exit
> createdb -O wine_cellar wine_cellar
connect from any user account (will prompt for password)
> psql -h localhost wine_cellar wine_cellar
```

