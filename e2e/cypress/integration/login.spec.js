describe('Register test', () => {
    const pass = 'password123';

    it('Registers a new user', () => {
        cy.visit('/register')

        cy.get('#Email')
            .type(`test${Math.floor(Math.random() * 1000)}@gmail.com`);

        cy.get('#Password')
            .type(pass);


        cy.get('form').submit();

        cy.url().should('eq', Cypress.config().baseUrl);        
    });

    it('Should be able to log in with the new user', () => {
        cy.visit('/login');

        cy.get('#Email')
            .type('admin@gmail.com');

        cy.get('#Password')
            .type(pass);


        cy.get('form').submit();

        cy.url().should('eq', Cypress.config().baseUrl); 

    });
})