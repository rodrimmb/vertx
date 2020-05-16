<!DOCTYPE html>
<html lang="es">
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no">
        <meta http-equiv="x-ua-compatible" content="ie=edge">
        <link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/4.5.0/css/bootstrap.min.css" integrity="sha384-9aIt2nRpC12Uk9gS9baDl411NQApFmC26EwAOH8WgZl5MYYxFfc+NcPb1dKGj7Sk" crossorigin="anonymous">
        <title>${title}</title>
    </head>
    <body>

        <div class="d-flex flex-column flex-md-row align-items-center p-3 px-md-4 mb-3 bg-white border-bottom shadow-sm">
            <h1 class="my-0 mr-md-auto font-weight-normal display-1">Wiki home</h1>

            <form action="/create" method="post">
                <div class="form-row align-items-center">
                    <div class="col-auto">
                        <label class="sr-only" for="name">Name</label>
                        <input type="text" class="form-control mb-2" id="name" name="name" placeholder="Page name">
                    </div>
                    <div class="col-auto">
                        <button type="submit" class="btn btn-primary mb-2">Create</button>
                    </div>
                </div>
            </form>
        </div>

        <div class="container">
            <div class="row">
                <h2>Pages:</h2>
            </div>
            <div class="row">
                <#if pages?has_content>
                    <ul>
                        <#list pages as page>
                            <li>
                                <a href="#">${page}</a>
                            </li>
                        </#list>
                    </ul>
                <#else>
                    <p>The Wiki is empty! Create new page</p>
                </#if>
            </div>
        </div>

    </body>
</html>