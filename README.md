# Context for Claude

I am making a wine cellar tracking web app. It is a learning project. I'm good
with back end Clojure and am using Clojurescript for the front end. I'm also a
bit frustrated with Viveno. Too many ads and difficult to do the simple tracking
that I want. I've made some good progress so far. Please consider this context
but don't jump right into the task till I focus you. Sometimes I will want
to have short conversations focused on tooling setup. I use nvim.

## Other notes

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
