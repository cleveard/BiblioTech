# BiblioTech

## Building the application

See: [BiblioTech Android App](BiblioTech/READEME.md) for details on building the application

## Locally Previewing the Github Pages

The BiblioTech Github Pages are kept in the /doc directory. Follow these steps to preview the pages locally.

### Setup

You need to install ruby, gems, bundler and jekyll to preview the pages. See [this page](https://docs.github.com/en/pages/setting-up-a-github-pages-site-with-jekyll/testing-your-github-pages-site-locally-with-jekyll) about local testing for more information.

#### Ruby

The way to install ruby on Windows is to use the [RubyInstaller](https://rubyinstaller.org/). Look at the [ruby install page](https://www.ruby-lang.org/en/documentation/installation/) for information on installing ruby on other systems.

If you are using a Cygwin bash shell on Windows you will need to add aliases to your .bashrc to get ruby apps to work. I added these aliases:
> ```bash
> alias gem='gem.bat'
> alias bundle='bundle.bat'
> alias jekyll='jekyll.bat'
> ```
Restart your shell or `source ~/.bashrc` to make the aliases effective.

#### Gems

[Download Gems](https://rubygems.org/pages/download) and install it using the directions on the page.

#### Bundler

Install [Bundler](https://bundler.io/) using

> gem install bundler

in a shell.

#### Jekyll

To install Jekyll
- Open a shell
- Change to the doc directory
- Run `bundle install`
- Follow [these instuctions](https://knightcodes.com/miscellaneous/2016/09/13/fix-github-metadata-error.html) to properly authenticate with Github while building the pages. You don't need to add the credentials to the Windows System Environment, your User Environment is good enough. You can also add it to you shell startup script if you only run jekyll from your shell.

### Serving the Pages

To view the pages locally
- Open a shell
- Change to the doc directory
- Run `bundle exec jekyll serve`
- Open `http://localhost:4000` in your browser. Be careful about browser caching. It may server old pages. If you open the developer console for your browser (F12 or Ctrl-Shift-I or Cmd-Shift-I) there are settings to disable caching and also a way to invalidate the cache and reload a page by right clicking on the browser refresh button.

To shutdown the server type Ctrl-C. My experience on Windows in a Cygwin bash shell is that Ctrl-C doesn't shutdown the server. You need to open the Windows task manager and end the running Ruby tasks. 